import { useEffect, useState } from 'react';
import { VideoRepository } from '../../data/VideoRepository';
export function VideoPlayer({ path }: { path: string }) {
  const [url, setUrl] = useState<string | null>(null);
  useEffect(() => {
    let active = true;
    if (path && VideoRepository.enabled)
      void VideoRepository.url(path).then((url) => {
        if (active) setUrl(url);
      });
    return () => {
      active = false;
    };
  }, [path]);
  return VideoRepository.enabled && url ? (
    <video className="post-video" controls playsInline preload="metadata" src={url} />
  ) : null;
}
