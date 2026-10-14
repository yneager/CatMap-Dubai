import { useEffect, useRef, useState } from 'react';
import { useParams } from 'react-router-dom';
import { PostRepository } from '../../data/PostRepository';
import { makePoster } from '../../util/poster';
import { download } from '../../util/share';
import { useAction } from '../../state';
import { AppTopBar, Loading, ErrorText } from '../components/Components';
import { t } from '../../i18n';
export default function PosterScreen() {
  const { id = '' } = useParams(),
    [url, setUrl] = useState(''),
    [error, setError] = useState(''),
    blob = useRef<Blob | null>(null),
    action = useAction();
  useEffect(() => {
    let active = true,
      objectUrl = '';
    void PostRepository.post(id)
      .then(async (p) => {
        if (!p) {
          setError(t('post_removed'));
          return;
        }
        const canvas = await makePoster(p);
        canvas.toBlob((value) => {
          if (!value) {
            if (active) setError(t('err_generic'));
            return;
          }
          objectUrl = URL.createObjectURL(value);
          if (active) {
            blob.current = value;
            setUrl(objectUrl);
          } else URL.revokeObjectURL(objectUrl);
        }, 'image/png');
      })
      .catch(() => setError(t('err_generic')));
    return () => {
      active = false;
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [id]);
  return (
    <>
      <AppTopBar title={t('poster')} />
      <div className="screen">
        <p>{t('poster_intro')}</p>
        <ErrorText text={error} />
        {url ? (
          <img className="poster-image" src={url} alt={t('poster')} />
        ) : (
          !error && <Loading rows={1} />
        )}
        <button
          className="primary"
          disabled={!url || action.busy}
          onClick={() =>
            void action.run(async () => {
              if (!blob.current) return;
              const file = new File([blob.current], 'catmap-poster.png', { type: 'image/png' });
              if (navigator.canShare?.({ files: [file] }))
                await navigator.share({ files: [file], title: t('poster') });
              else download(blob.current, 'catmap-poster.png');
            })
          }
        >
          {t('share_poster')}
        </button>
        <button
          className="outlined"
          disabled={!url}
          onClick={() => {
            if (blob.current) download(blob.current, 'catmap-poster.png');
          }}
        >
          {t('save')}
        </button>
      </div>
    </>
  );
}
