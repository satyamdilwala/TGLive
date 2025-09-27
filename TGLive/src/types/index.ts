// This file exports TypeScript interfaces and types used throughout the application to ensure type safety.

export interface Config {
    databaseUrl: string;
    apiKey: string;
}

export interface User {
    id: string;
    name: string;
    email: string;
}

export interface Response<T> {
    data: T;
    error?: string;
}