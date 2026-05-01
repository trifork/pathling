package au.csiro.fhir.export.download;

import static au.csiro.utils.TimeoutUtils.hasExpired;
import static au.csiro.utils.TimeoutUtils.toTimeoutAt;

import au.csiro.fhir.export.BulkExportException;
import au.csiro.fhir.export.BulkExportException.DownloadError;
import au.csiro.fhir.export.BulkExportException.HttpError;
import au.csiro.fhir.export.BulkExportException.Timeout;
import au.csiro.filestore.FileStore;
import au.csiro.filestore.FileStore.FileHandle;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.util.EntityUtils;

/**
 * A template class for concurrent download of multiple URLs into a file store. The file store can
 * be any concrete implementation of the {@link FileStore} abstraction.
 *
 * <p>This implementation fails fast: all the downloads are terminated on the first failure in any
 * of the downloads.
 *
 * <p>No cleanup is performed on failure - partial results may be left for some of the URLs.
 *
 * <p>This class shadows the upstream {@code au.csiro.fhir:bulk-export} version to add support for
 * FHIR servers that return manifest output URLs as {@code Binary} resources rather than streaming
 * the raw bulk-data file. Three response shapes are supported:
 *
 * <ol>
 *   <li>Raw body (e.g. {@code application/fhir+ndjson}, {@code application/octet-stream}) — the
 *       bytes are written verbatim to the destination, matching the upstream behaviour.
 *   <li>FHIR {@code Binary} JSON with inline {@code data} — the base64 payload is decoded and
 *       written.
 *   <li>FHIR {@code Binary} JSON with externalised content (HAPI's {@code externalized-binary-id}
 *       extension on {@code _data}) — the content is fetched via the {@code $binary-access-read}
 *       operation against the same URL.
 * </ol>
 */
@Slf4j
public class UrlDownloadTemplate {

  private static final ObjectMapper JSON = new ObjectMapper();

  /** Marker for FHIR JSON content type, matched case-insensitively as a substring. */
  private static final String FHIR_JSON_MIME = "fhir+json";

  /**
   * HAPI binary access operation appended to the source URL when the content is externalised. For
   * top-level {@code Binary} resources the operation is invoked without a {@code path} parameter —
   * HAPI streams the inflated content directly. The {@code path=...} variant in the specification
   * only applies to {@code Attachment.data}-typed fields embedded in other resources, where it
   * would be rejected for top-level {@code Binary.data} with HAPI-1336.
   */
  private static final String BINARY_ACCESS_READ_PATH = "/$binary-access-read";

  /** A single entry in the list of URLs to download. */
  @Value
  public static class UrlDownloadEntry {

    /** The source URL to download from. */
    @Nonnull URI source;

    /** The destination file to write the downloaded content to. */
    @Nonnull FileHandle destination;
  }

  /**
   * The HTTP client to use for downloading. The lifecycle of the client should be managed
   * externally.
   */
  @Nonnull HttpClient httpClient;

  /**
   * The executor service to use for concurrent downloads. The lifecycle of the executor should be
   * managed externally.
   */
  @Nonnull ExecutorService executorService;

  @Value
  class UriDownloadTask implements Callable<Long> {

    @Nonnull URI source;

    @Nonnull FileHandle destination;

    @Override
    public Long call() throws Exception {
      log.debug("Starting download from:  {}  to: {}", source, destination);
      final HttpResponse result = httpClient.execute(new HttpGet(source));
      try {
        if (result.getStatusLine().getStatusCode() != 200) {
          log.error("Failed to download: {}. Status: {}", source, result.getStatusLine());
          throw new HttpError(
              "Failed to download: " + source, result.getStatusLine().getStatusCode());
        }
        if (isFhirJson(result)) {
          // FHIR servers (e.g. HAPI with externalised binary storage) may answer the manifest URL
          // with a Binary resource wrapper rather than streaming the bulk-data file. Resolve the
          // wrapper to the actual content.
          final byte[] body = EntityUtils.toByteArray(result.getEntity());
          return resolveBinaryResource(body);
        }
        try (final InputStream is = result.getEntity().getContent()) {
          final long bytesWritten = destination.writeAll(is);
          log.debug("Downloaded {} bytes from:  {}  to: {}", bytesWritten, source, destination);
          return bytesWritten;
        }
      } finally {
        EntityUtils.consumeQuietly(result.getEntity());
      }
    }

    private long resolveBinaryResource(@Nonnull final byte[] jsonBody) throws Exception {
      final JsonNode root = JSON.readTree(jsonBody);
      final String resourceType = root.path("resourceType").asText("");
      if (!"Binary".equals(resourceType)) {
        throw new BulkExportException(
            "Expected FHIR Binary resource at "
                + source
                + " but got resourceType="
                + (resourceType.isEmpty() ? "<missing>" : resourceType));
      }

      final JsonNode data = root.get("data");
      if (data != null && data.isTextual() && !data.asText().isEmpty()) {
        // Inline base64 payload — decode and write directly.
        final byte[] decoded = Base64.getDecoder().decode(data.asText());
        try (final InputStream is = new ByteArrayInputStream(decoded)) {
          final long bytesWritten = destination.writeAll(is);
          log.debug(
              "Wrote {} inline binary bytes from: {} to: {}", bytesWritten, source, destination);
          return bytesWritten;
        }
      }

      // No inline data: assume HAPI-style externalised binary and fetch via $binary-access-read.
      final URI accessReadUri = URI.create(source + BINARY_ACCESS_READ_PATH);
      log.debug("Resolving externalised binary via: {}", accessReadUri);
      final HttpResponse contentResp = httpClient.execute(new HttpGet(accessReadUri));
      try {
        if (contentResp.getStatusLine().getStatusCode() != 200) {
          throw new HttpError(
              "Failed to fetch externalised binary content: " + accessReadUri,
              contentResp.getStatusLine().getStatusCode());
        }
        try (final InputStream is = contentResp.getEntity().getContent()) {
          final long bytesWritten = destination.writeAll(is);
          log.debug(
              "Wrote {} externalised binary bytes from: {} to: {}",
              bytesWritten,
              accessReadUri,
              destination);
          return bytesWritten;
        }
      } finally {
        EntityUtils.consumeQuietly(contentResp.getEntity());
      }
    }

    private boolean isFhirJson(@Nonnull final HttpResponse response) {
      final HttpEntity entity = response.getEntity();
      if (entity == null) {
        return false;
      }
      final Header contentType = entity.getContentType();
      if (contentType == null) {
        return false;
      }
      final String value = contentType.getValue();
      return value != null && value.toLowerCase(Locale.ROOT).contains(FHIR_JSON_MIME);
    }
  }

  /**
   * Creates a new instance of the template.
   *
   * @param httpClient the HTTP client to use for downloading (its life cycle should be managed
   *     externally).
   * @param executorService the executor service to use for concurrent downloads (its life cycle
   *     should be managed externally).
   */
  public UrlDownloadTemplate(
      @Nonnull final HttpClient httpClient, @Nonnull final ExecutorService executorService) {
    this.httpClient = httpClient;
    this.executorService = executorService;
  }

  /**
   * Downloads the given URLs concurrently to provided destinations in a {@link FileStore}.
   *
   * @param urlsToDownload the list of URLs to download together with their desired destinations.
   * @param timeout the maximum time to wait for the downloads to complete. Zero or negative values
   *     are treated as infinite.
   * @return a list of the number of bytes downloaded for each URL in the same order as the input
   */
  public List<Long> download(
      @Nonnull final List<UrlDownloadEntry> urlsToDownload, @Nonnull final Duration timeout) {

    final Instant timeoutAt = toTimeoutAt(timeout);

    final Collection<Callable<Long>> tasks =
        urlsToDownload.stream()
            .map(e -> new UriDownloadTask(e.getSource(), e.getDestination()))
            .collect(Collectors.toUnmodifiableList());

    // submitting the task independently
    final List<Future<Long>> futures = tasks.stream().map(executorService::submit).toList();

    try {
      // wait for all the futures to complete or any to fail
      while (!futures.stream().allMatch(Future::isDone)
          && futures.stream().noneMatch(f -> asException(f).isPresent())) {
        if (hasExpired(timeoutAt)) {
          log.error("Cancelling download due to time limit {} exceeded at: {}", timeout, timeoutAt);
          throw new Timeout("Download timed out at: " + timeout);
        }
        TimeUnit.SECONDS.sleep(1);
      }
      // check if any of the futures failed
      futures.stream()
          .map(UrlDownloadTemplate::asException)
          .filter(Optional::isPresent)
          .flatMap(Optional::stream)
          .findAny()
          .ifPresent(
              e -> {
                log.error("Cancelling the download because of '{}'", unwrap(e).getMessage());
                throw new DownloadError("Download failed", unwrap(e));
              });
      return futures.stream().map(UrlDownloadTemplate::asValue).collect(Collectors.toList());
    } catch (final InterruptedException ex) {
      log.debug("Download interrupted", ex);
      Thread.currentThread().interrupt();
      throw new BulkExportException.SystemError("Download interrupted", ex);
    } finally {
      // cancel all the futures
      futures.forEach(f -> f.cancel(true));
    }
  }

  private static <T> Optional<Exception> asException(@Nonnull final Future<T> f) {
    try {
      if (f.isDone()) {
        f.get();
      }
      return Optional.empty();
    } catch (final Exception ex) {
      return Optional.of(ex);
    }
  }

  private static <T> T asValue(@Nonnull final Future<T> f) {
    if (!f.isDone()) {
      throw new IllegalStateException("Future is not done");
    }
    try {
      return f.get();
    } catch (final Exception ex) {
      throw new IllegalStateException("Unexpected exception from successful future", ex);
    }
  }

  private static Throwable unwrap(@Nonnull final Exception futureEx) {
    if (futureEx instanceof ExecutionException) {
      return futureEx.getCause();
    } else {
      return futureEx;
    }
  }
}
