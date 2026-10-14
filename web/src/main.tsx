import React from 'react';
import { createRoot } from 'react-dom/client';
import '@fontsource/roboto/latin-400.css';
import '@fontsource/roboto/latin-500.css';
import '@fontsource/roboto/latin-600.css';
import '@fontsource/roboto/latin-700.css';
import '@fontsource/noto-sans-arabic/arabic-400.css';
import '@fontsource/noto-sans-arabic/arabic-500.css';
import '@fontsource/noto-sans-arabic/arabic-600.css';
import '@fontsource/noto-sans-arabic/arabic-700.css';
import InstallRoot from './platform/InstallRoot';
import { captureInstallEvents } from './platform/install';
import { startAppFeel } from './platform/appFeel';
import { startUpdates } from './platform/updates';
import './ui/theme/theme.css';
import './platform/appFeel.css';
captureInstallEvents();
startAppFeel();
startUpdates();
createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <InstallRoot />
  </React.StrictMode>,
);
