import { useState } from 'react';
import { useApp, useList, useAction } from '../../state';
import {
  HelpDirectoryRepository,
  HelpContactDefaults,
  HelpContactFromDoc,
  type HelpContact,
} from '../../data/HelpDirectoryRepository';
import { t } from '../../i18n';
import {
  AppTopBar,
  Loading,
  EmptyState,
  Field,
  Dialog,
  Switch,
  ErrorText,
} from '../components/Components';
export default function HelpDirectoryScreen() {
  const contacts = useList(HelpDirectoryRepository.all, HelpContactFromDoc, 'help'),
    { profile } = useApp(),
    admin = profile?.role === 'admin',
    action = useAction(),
    [editing, setEditing] = useState<HelpContact | null>(null);
  const change = (p: Partial<HelpContact>) => setEditing((c) => (c ? { ...c, ...p } : c));
  return (
    <>
      <AppTopBar title={t('help_directory')}>
        {admin && (
          <button className="text-button" onClick={() => setEditing({ ...HelpContactDefaults })}>
            {t('add')}
          </button>
        )}
      </AppTopBar>
      <div className="screen">
        {!contacts ? (
          <Loading />
        ) : contacts.length ? (
          contacts
            .sort((a, b) => a.name.toLowerCase().localeCompare(b.name.toLowerCase()))
            .map((c) => (
              <article className="card" key={c.id}>
                <h3 dir="auto">{c.name}</h3>
                <small>
                  {t('help_kind_' + c.kind)} {c.demo && t('demo_contact')}
                </small>
                <p dir="auto">{c.address}</p>
                <p dir="auto">{c.note}</p>
                <div className="row wrap">
                  {c.phone && (
                    <a className="outlined" href={'tel:' + c.phone.replace(/[^+\d]/g, '')}>
                      {c.phone}
                    </a>
                  )}
                  {c.website.startsWith('https://') && (
                    <a className="text-button" href={c.website} target="_blank" rel="noreferrer">
                      {t('help_website')}
                    </a>
                  )}
                  {admin && (
                    <button className="text-button" onClick={() => setEditing(c)}>
                      {t('edit')}
                    </button>
                  )}
                </div>
              </article>
            ))
        ) : (
          <EmptyState text={t('no_help_contacts')} />
        )}
      </div>
      {editing && (
        <Dialog title={t('help_directory')} onClose={() => setEditing(null)}>
          <form
            onSubmit={(e) => {
              e.preventDefault();
              void action.run(async () => {
                await HelpDirectoryRepository.save(editing);
                setEditing(null);
              }, t('saved'));
            }}
          >
            <Field
              label={t('name')}
              value={editing.name}
              onChange={(name) => change({ name })}
              limit={80}
              required
            />
            <label className="field">
              <span>{t('record_type')}</span>
              <select value={editing.kind} onChange={(e) => change({ kind: e.target.value })}>
                {['vet', 'shelter', 'emergency', 'other'].map((k) => (
                  <option key={k} value={k}>
                    {t('help_kind_' + k)}
                  </option>
                ))}
              </select>
            </label>
            <Field
              label={t('phone_number')}
              value={editing.phone}
              onChange={(phone) => change({ phone })}
              limit={40}
              type="tel"
            />
            <Field
              label={t('help_address')}
              value={editing.address}
              onChange={(address) => change({ address })}
              limit={300}
            />
            <Field
              label={t('help_website')}
              value={editing.website}
              onChange={(website) => change({ website })}
              limit={300}
              pattern="https://.*"
            />
            <Field
              label={t('note')}
              value={editing.note}
              onChange={(note) => change({ note })}
              limit={600}
              multiline
            />
            <Switch
              label={t('demo_contact')}
              checked={editing.demo}
              onChange={(demo) => change({ demo })}
            />
            <ErrorText text={action.error} />
            <button className="primary" disabled={action.busy}>
              {t('save')}
            </button>
            {editing.id && (
              <button
                type="button"
                className="text-button danger"
                disabled={action.busy}
                onClick={() =>
                  void action.run(async () => {
                    await HelpDirectoryRepository.delete(editing);
                    setEditing(null);
                  })
                }
              >
                {t('delete')}
              </button>
            )}
          </form>
        </Dialog>
      )}
    </>
  );
}
