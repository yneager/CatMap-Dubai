import { BrowserRouter } from 'react-router-dom';
import { AppProvider } from '../state';
import App from '../App';
export default function Runtime() {
  return (
    <BrowserRouter>
      <AppProvider>
        <App />
      </AppProvider>
    </BrowserRouter>
  );
}
