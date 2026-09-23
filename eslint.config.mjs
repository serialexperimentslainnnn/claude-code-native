import js from '@eslint/js';
import globals from 'globals';
import prettier from 'eslint-config-prettier';

export default [
  {
    ignores: [
      'frontend/src/main/resources/jcef/marked.min.js',
      'frontend/src/main/resources/jcef/purify.min.js',
      'frontend/src/main/resources/jcef/highlight.min.js',
      '**/build/**',
      'node_modules/**',
      '.intellijPlatform/**',
      '.claudetools/**',
    ],
  },

  js.configs.recommended,

  {
    files: ['frontend/src/main/resources/jcef/*.js'],
    languageOptions: {
      ecmaVersion: 2019,
      sourceType: 'script',
      globals: {
        ...globals.browser,
        cc: 'writable',
        CC: 'writable',
        __ccSend: 'readonly',
        marked: 'readonly',
        DOMPurify: 'readonly',
        hljs: 'readonly',
      },
    },
    rules: {
      'no-eval': 'error',
      'no-implied-eval': 'error',
      'no-new-func': 'error',
      'no-script-url': 'error',
      eqeqeq: ['error', 'smart'],
      'no-var': 'off',
      'no-implicit-globals': 'error',
      'no-unused-vars': [
        'error',
        { argsIgnorePattern: '^_', varsIgnorePattern: '^_', caughtErrors: 'none' },
      ],
      'no-empty': ['error', { allowEmptyCatch: true }],
    },
  },

  {
    files: ['src/test/frontend/**/*.js'],
    languageOptions: {
      ecmaVersion: 2022,
      sourceType: 'module',
      globals: {
        ...globals.node,
        ...globals.browser,
        describe: 'readonly',
        it: 'readonly',
        test: 'readonly',
        expect: 'readonly',
        beforeEach: 'readonly',
        afterEach: 'readonly',
        beforeAll: 'readonly',
        afterAll: 'readonly',
        vi: 'readonly',
      },
    },
  },

  {
    files: ['*.config.js', '*.config.cjs'],
    languageOptions: {
      ecmaVersion: 2022,
      sourceType: 'commonjs',
      globals: { ...globals.node },
    },
  },

  prettier,
];
