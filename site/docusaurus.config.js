// @ts-check
// Note: type annotations allow type checking and IDEs autocompletion

const lightCodeTheme = require("prism-react-renderer/themes/github");
const darkCodeTheme = require("prism-react-renderer/themes/dracula");

/** @type {import("@docusaurus/types").Config} */
const config = {
  title: "Pathling",
  tagline: "Advanced FHIR&reg; analytics server",
  url: "https://pathling.csiro.au",
  baseUrl: "/",
  onBrokenLinks: "throw",
  onBrokenMarkdownLinks: "warn",
  favicon: "img/favicon.ico",

  organizationName: "aehrc",
  projectName: "pathling",
  trailingSlash: false,

  i18n: {
    defaultLocale: "en",
    locales: ["en"]
  },

  presets: [
    [
      "classic",
      /** @type {import("@docusaurus/preset-classic").Options} */
      ({
        docs: {
          sidebarPath: require.resolve("./sidebars.js"),
          editUrl: "https://github.com/aehrc/pathling/tree/main/site/"
        },
        theme: {
          customCss: require.resolve("./src/css/custom.css")
        }
      })
    ]
  ],

  themeConfig:
  /** @type {import("@docusaurus/preset-classic").ThemeConfig} */
    ({
      navbar: {
        title: null,
        logo: {
          alt: "Pathling",
          src: "images/logo-colour.svg",
          srcDark: "images/logo-colour-dark.svg"
        },
        items: [
          {
            type: "doc",
            position: "left",
            docId: "index",
            label: "Overview"
          },
          {
            type: "doc",
            position: "left",
            docId: "encoders/index",
            label: "Encoders"
          },
          {
            type: "docSidebar",
            position: "left",
            sidebarId: "fhirpath",
            label: "FHIRPath"
          },
          {
            type: "docSidebar",
            position: "left",
            sidebarId: "server",
            label: "Server"
          },
          {
            type: "docSidebar",
            position: "left",
            sidebarId: "libraries",
            label: "Libraries"
          },
          {
            href: "https://github.com/aehrc/pathling",
            label: "GitHub",
            position: "right"
          }
        ]
      },
      footer: {
        copyright: `This documentation is dedicated to the public domain via <a href="https://creativecommons.org/publicdomain/zero/1.0/">CC0</a>.`
      },
      prism: {
        theme: lightCodeTheme,
        darkTheme: darkCodeTheme,
        additionalLanguages: ["java", "scala"]
      }
    })
};

module.exports = config;
