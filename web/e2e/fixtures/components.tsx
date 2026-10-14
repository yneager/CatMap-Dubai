import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import '@fontsource/roboto/latin-400.css';
import '@fontsource/roboto/latin-500.css';
import '@fontsource/roboto/latin-600.css';
import '@fontsource/roboto/latin-700.css';
import '@fontsource/noto-sans-arabic/arabic-400.css';
import '@fontsource/noto-sans-arabic/arabic-700.css';
import { AppProvider } from '../../src/state';
import { PostDefaults } from '../../src/data/model';
import { PostCard, StatusPill } from '../../src/ui/components/Components';
import { PostDetailHeader } from '../../src/ui/components/PostDetailHeader';
import { FilterSheet } from '../../src/ui/explore/Filtering';
import { filterDefaults } from '../../src/data/SettingsRepository';
import { setLanguage } from '../../src/i18n';
import '../../src/ui/theme/theme.css';

const params = new URLSearchParams(location.search);
document.documentElement.dataset.theme = params.get('theme') ?? 'light';
setLanguage(params.get('language') ?? 'en');
const post = {
  ...PostDefaults,
  id: 'fixture',
  name: 'Amber',
  category: 'lost',
  animalType: 'cat',
  description: 'Orange coat, teal collar. Last seen near the park.',
  neighborhood: 'deira',
  reward: 250,
};
const component = params.get('component');
createRoot(document.getElementById('root')!).render(
  <BrowserRouter>
    <AppProvider>
      <div id="fixture" style={{ width: 360, background: 'var(--surface)' }}>
        {component === 'post-card' ? (
          <PostCard post={post} />
        ) : component === 'post-grid-card' ? (
          <PostCard post={post} grid />
        ) : component === 'detail-header' ? (
          <div style={{ padding: 16 }}>
            <PostDetailHeader post={post} />
          </div>
        ) : component === 'status-badge' ? (
          <StatusPill value="reunited" />
        ) : (
          <FilterSheet filters={filterDefaults} onClose={() => {}} />
        )}
      </div>
    </AppProvider>
  </BrowserRouter>,
);
