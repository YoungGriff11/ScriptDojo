import js from '@eslint/js'
import globals from 'globals'
import reactHooks from 'eslint-plugin-react-hooks'
import reactRefresh from 'eslint-plugin-react-refresh'
import { defineConfig, globalIgnores } from 'eslint/config'

/**
 * ESLint flat config for the ScriptDojo React frontend.
 * Uses ESLint's newer flat config format (eslint.config.js) rather than
 * the legacy .eslintrc format.
 * Plugins applied:
 * - @eslint/js          — ESLint's built-in recommended ruleset
 * - eslint-plugin-react-hooks — enforces the Rules of Hooks and exhaustive-deps
 * - eslint-plugin-react-refresh — warns when components are not safe for Vite HMR
 */
export default defineConfig([

  // Exclude the production build output from linting
  globalIgnores(['dist']),

  {
    // Apply this config block to all JavaScript and JSX source files
    files: ['**/*.{js,jsx}'],

    extends: [
      js.configs.recommended,                    // Core JS rules (no-undef, no-unused-vars, etc.)
      reactHooks.configs.flat.recommended,        // Rules of Hooks + exhaustive-deps warnings
      reactRefresh.configs.vite,                  // HMR safety checks for Vite projects
    ],

    languageOptions: {
      ecmaVersion: 2020,
      globals: globals.browser,   // Adds browser globals (window, document, fetch, etc.)
      parserOptions: {
        ecmaVersion: 'latest',
        ecmaFeatures: { jsx: true },  // Enables JSX parsing
        sourceType: 'module',          // Treat files as ES modules
      },
    },

    rules: {
      // Warn on unused variables, but ignore names that are ALL_CAPS or start with
      // an underscore — covers React component constants and intentionally unused imports
      'no-unused-vars': ['error', { varsIgnorePattern: '^[A-Z_]' }],
    },
  },
])