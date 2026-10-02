module.exports = ({ mode }) => {
  const bench = mode === 'bench';
  return {
    test: {
      environment: 'jsdom',
      globals: true,
      include: [bench ? 'src/test/frontend/**/*.bench.test.js' : 'src/test/frontend/**/*.test.js'],
      exclude: bench ? [] : ['src/test/frontend/**/*.bench.test.js'],
      restoreMocks: true,
      clearMocks: true,
      reporters: process.env.CI ? ['default', 'junit'] : ['default'],
      outputFile: { junit: 'build/reports/frontend/junit.xml' },
    },
  };
};
