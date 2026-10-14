import { randomBytes } from 'node:crypto';
import { account, authBase, firestoreBase, fields, headers } from './helpers';
export async function fakePhotoWorker(
  uid: string,
  answer: () => { kind: string; animalType?: string; phrase?: string },
) {
  const worker = await account();
  const claim = await fetch(
    authBase + '/identitytoolkit.googleapis.com/v1/accounts:update?key=demo-key',
    {
      method: 'POST',
      headers,
      body: JSON.stringify({
        localId: worker.uid,
        customAttributes: JSON.stringify({ catmapAiWorker: true }),
      }),
    },
  );
  if (!claim.ok) throw new Error('Worker claim setup failed');
  const signed = await fetch(
    authBase + '/identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=demo-key',
    {
      method: 'POST',
      headers,
      body: JSON.stringify({
        email: worker.email,
        password: worker.password,
        returnSecureToken: true,
      }),
    },
  );
  if (!signed.ok) throw new Error('Worker sign-in failed');
  const token = (await signed.json()).idToken;
  const workerHeaders = { Authorization: 'Bearer ' + token, 'Content-Type': 'application/json' };
  const health = async (online: boolean) => {
    const r = await fetch(firestoreBase + '/config/aiWorker', {
      method: 'PATCH',
      headers: workerHeaders,
      body: JSON.stringify({
        fields: {
          ...fields({
            online,
            model: 'fake-' + randomBytes(3).toString('hex'),
            provider: 'test',
            same: 0.75,
            maybe: 0.6,
          }),
          lastSeen: { timestampValue: new Date().toISOString() },
        },
      }),
    });
    if (!r.ok) throw new Error('Claimed worker heartbeat refused: ' + r.status);
  };
  await health(true);
  let stopped = false;
  const jobs: { kind: string; photos: number; ids: number }[] = [];
  let failure: unknown;
  const run = (async () => {
    while (!stopped) {
      const r = await fetch(firestoreBase + '/aiJobs/' + uid, { headers: workerHeaders });
      if (r.ok) {
        const raw = await r.json(),
          f = raw.fields;
        if (f.status?.stringValue === 'pending') {
          const count = f.photos?.arrayValue?.values?.length ?? 0;
          jobs.push({
            kind: f.kind?.stringValue,
            photos: count,
            ids: f.ids?.arrayValue?.values?.length ?? 0,
          });
          const verdict = answer();
          const result = await fetch(firestoreBase + '/aiJobs/' + uid, {
            method: 'PATCH',
            headers: workerHeaders,
            body: JSON.stringify({
              fields: {
                ...f,
                ...fields({
                  status: 'done',
                  answeredRequestId: f.requestId.stringValue,
                  classification: Array.from({ length: count }, () => ({
                    ...verdict,
                    score: 0.96,
                  })),
                  results: [],
                }),
              },
            }),
          });
          if (!result.ok) throw new Error('Claimed worker answer refused: ' + result.status);
        }
      } else if (r.status !== 404) throw new Error('Claimed worker job read refused: ' + r.status);
      await new Promise((resolve) => setTimeout(resolve, 100));
    }
  })().catch((error) => {
    failure = error;
  });
  return {
    jobs,
    stop: async () => {
      stopped = true;
      await run;
      await health(false);
      if (failure) throw failure;
    },
  };
}
