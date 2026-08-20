import axios from 'axios';

const api = axios.create({
  // Vite 환경변수 문법을 사용하여 실행 환경에 맞는 URL을 자동 할당
  baseURL: import.meta.env.VITE_API_BASE_URL, 
  headers: {
    'Content-Type': 'application/json',
  },
});

export default api;