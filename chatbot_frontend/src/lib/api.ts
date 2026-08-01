import axios from 'axios';

// Configure Axios with Backend Port
export const api = axios.create({
    baseURL: 'http://localhost:8080',
});

// Attach Authorization Header dynamically
api.interceptors.request.use((config) => {
    const token = localStorage.getItem('token');
    if (token) {
        config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
});
