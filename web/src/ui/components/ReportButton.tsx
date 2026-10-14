import { useState } from 'react';
import { useApp, useAction } from '../../state';
import { t } from '../../i18n';
import { enums } from '../../data/model/Enums';
import { ModerationRepository } from '../../data/ModerationRepository';
import { Dialog, Chips, Field, ErrorText } from './Components';
export function ReportButton({ type, id, label }: { type: string; id: string; label: string }) {
  const { profile } = useApp(),
    action = useAction(),
    [open, setOpen] = useState(false),
    [reason, setReason] = useState('other'),
    [details, setDetails] = useState('');
  if (!profile) return null;
  return (
    <>
      <button className="text-button" onClick={() => setOpen(true)}>
        {t('report_content')}
      </button>
      {open && (
        <Dialog title={t('report_content')} onClose={() => setOpen(false)}>
          <p>{t('report_content_hint')}</p>
          <Chips options={enums.ReportReason} value={reason} onChange={setReason} />
          <Field
            label={t('details_optional')}
            value={details}
            onChange={setDetails}
            limit={500}
            multiline
          />
          <ErrorText text={action.error} />
          <button
            className="primary"
            disabled={action.busy}
            onClick={() =>
              void action.run(async () => {
                await ModerationRepository.reportContent(type, id, label, reason, details, profile);
                setOpen(false);
              }, t('report_thanks'))
            }
          >
            {t('send')}
          </button>
        </Dialog>
      )}
    </>
  );
}
