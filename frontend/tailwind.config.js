/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{ts,tsx}"],
  theme: {
    extend: {
      colors: {
        ink: "#0E1322",
        panel: "#161D33",
        raised: "#1E2744",
        edge: "#2D3862",
        paper: "#F1E7CC",
        paperdim: "#D9CDAB",
        brass: "#E2A93B",
        mint: "#4CC3A5",
        coral: "#EF6B5B",
        mist: "#9DA9CC",
      },
      fontFamily: {
        display: ['"Bricolage Grotesque"', "ui-sans-serif", "system-ui", "sans-serif"],
        sans: ['"IBM Plex Sans"', "ui-sans-serif", "system-ui", "sans-serif"],
      },
      keyframes: {
        pop: { "0%": { transform: "scale(0.85)", opacity: "0" }, "100%": { transform: "scale(1)", opacity: "1" } },
      },
      animation: { pop: "pop 160ms ease-out" },
    },
  },
  plugins: [],
};
