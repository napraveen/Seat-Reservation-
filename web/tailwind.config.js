/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  darkMode: "class",
  theme: {
    extend: {
      colors: {
        base: {
          950: 'rgb(var(--c-base-950) / <alpha-value>)',
          900: 'rgb(var(--c-base-900) / <alpha-value>)',
          850: 'rgb(var(--c-base-850) / <alpha-value>)',
          800: 'rgb(var(--c-base-800) / <alpha-value>)',
          700: 'rgb(var(--c-base-700) / <alpha-value>)',
          600: 'rgb(var(--c-base-600) / <alpha-value>)',
        },
        ink: {
          50: 'rgb(var(--c-ink-50) / <alpha-value>)',
          100: 'rgb(var(--c-ink-100) / <alpha-value>)',
          200: 'rgb(var(--c-ink-200) / <alpha-value>)',
          300: 'rgb(var(--c-ink-300) / <alpha-value>)',
          400: 'rgb(var(--c-ink-400) / <alpha-value>)',
          500: 'rgb(var(--c-ink-500) / <alpha-value>)',
          600: 'rgb(var(--c-ink-600) / <alpha-value>)',
        },
        cyan: {
          100: 'rgb(var(--c-cyan-100) / <alpha-value>)',
          200: 'rgb(var(--c-cyan-200) / <alpha-value>)',
          300: 'rgb(var(--c-cyan-300) / <alpha-value>)',
          400: 'rgb(var(--c-cyan-400) / <alpha-value>)',
          500: 'rgb(var(--c-cyan-500) / <alpha-value>)',
        },
        iris: {
          200: 'rgb(var(--c-iris-200) / <alpha-value>)',
          300: 'rgb(var(--c-iris-300) / <alpha-value>)',
          400: 'rgb(var(--c-iris-400) / <alpha-value>)',
          500: 'rgb(var(--c-iris-500) / <alpha-value>)',
        },
        emerald: {
          100: 'rgb(var(--c-emerald-100) / <alpha-value>)',
          200: 'rgb(var(--c-emerald-200) / <alpha-value>)',
          300: 'rgb(var(--c-emerald-300) / <alpha-value>)',
          400: 'rgb(var(--c-emerald-400) / <alpha-value>)',
          500: 'rgb(var(--c-emerald-500) / <alpha-value>)',
        },
        red: {
          100: 'rgb(var(--c-red-100) / <alpha-value>)',
          200: 'rgb(var(--c-red-200) / <alpha-value>)',
          300: 'rgb(var(--c-red-300) / <alpha-value>)',
          400: 'rgb(var(--c-red-400) / <alpha-value>)',
          500: 'rgb(var(--c-red-500) / <alpha-value>)',
        },
        amber: {
          100: 'rgb(var(--c-amber-100) / <alpha-value>)',
          200: 'rgb(var(--c-amber-200) / <alpha-value>)',
          300: 'rgb(var(--c-amber-300) / <alpha-value>)',
          400: 'rgb(var(--c-amber-400) / <alpha-value>)',
          500: 'rgb(var(--c-amber-500) / <alpha-value>)',
        },
        hairline: 'rgb(var(--hairline))',
        well: 'rgb(var(--well))',
        eyebrow: 'rgb(var(--eyebrow))',
      },
      boxShadow: {
        'elev-1': 'var(--elev-1)',
        'elev-2': 'var(--elev-2)',
        'elev-3': 'var(--elev-3)',
        'glow-cyan': 'var(--glow-cyan)',
        'glow-iris': 'var(--glow-iris)',
      }
    },
  },
  plugins: [],
}
