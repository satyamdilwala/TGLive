// src/config/config.ts

const config = {
    database: {
        host: 'localhost',
        port: 5432,
        username: 'your_username',
        password: 'your_password',
        database: 'your_database',
    },
    apiKeys: {
        serviceA: 'your_service_a_api_key',
        serviceB: 'your_service_b_api_key',
    },
    server: {
        port: 3000,
    },
};

export default config;