export default {
    testEnvironment: 'node',
    transform:{},
    moduleNameMapper:{
        '^@/(.*)$': '<rootDir>/src/$1',
    },
    roots: ['<rootDir>/tests'],      
    testMatch: ['**/*.test.js'],    
};