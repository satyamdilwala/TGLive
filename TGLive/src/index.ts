import express from 'express';
import { config } from './config/config';

const app = express();
const PORT = process.env.PORT || 3000;

// Middleware and configurations
app.use(express.json());

// Example route
app.get('/', (req, res) => {
    res.send('Welcome to TGLive!');
});

// Start the server
app.listen(PORT, () => {
    console.log(`Server is running on http://localhost:${PORT}`);
});