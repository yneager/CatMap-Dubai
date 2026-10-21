import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { PostCard } from '../../src/ui/components/Components';
import { PostFromDoc, AppNotificationFromDoc } from '../../src/data/model';
import { NotificationText } from '../../src/notifications/NotificationDelivery';
const post = PostFromDoc('p', {
  name: '<img src=x onerror=alert(1)>',
  color: 5,
  photoIds: [1],
  description: '<script>evil()</script>',
});
const notification = AppNotificationFromDoc('n', {
  type: 'message',
  params: { user: '<script>evil()</script>', text: '<b>plain</b>' },
  route: 3,
});
createRoot(document.getElementById('root')!).render(
  <BrowserRouter>
    <PostCard post={post} />
    <section data-notification>
      {NotificationText.format(notification).map((text, i) => (
        <p key={i}>{text}</p>
      ))}
    </section>
  </BrowserRouter>,
);
