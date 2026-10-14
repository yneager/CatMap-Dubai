import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { enums } from '../../data/model/Enums';
import { CatMapIcons } from '../../icons/CatMapIcons';
import { Sheet, Icon } from '../components/Components';
import { t } from '../../i18n';
export function PostChooser({ onClose }: { onClose: () => void }) {
  const navigate = useNavigate(),
    [sitting, setSitting] = useState(false);
  const choices = sitting
    ? [
        {
          key: 'offer',
          label: 'sitting_offer',
          hint: 'sitting_offer_hint',
          icon: CatMapIcons.SITTER,
          color: '#5E35B1',
          route: '/sitter-edit',
        },
        {
          key: 'request',
          label: 'sitting_request',
          hint: 'sitting_request_hint',
          icon: CatMapIcons.CALENDAR,
          color: '#00897B',
          route: '/sitters',
        },
      ]
    : [
        ...[
          ...enums.PostCategory.filter((c) => c.key === 'street'),
          ...enums.PostCategory.filter((c) => !['street', 'spotted', 'other'].includes(c.key)),
        ].map((c) => ({
          ...c,
          label: c.key === 'street' ? 'chooser_street' : c.label,
          hint: 'hint_' + c.key,
          icon: CatMapIcons.category(c.key),
          route: '/post-new?category=' + c.key,
        })),
        {
          key: 'rescue',
          label: 'report_emergency',
          hint: 'chooser_rescue_hint',
          icon: CatMapIcons.RESCUE,
          color: '#C62828',
          route: '/rescue-new',
        },
        {
          key: 'sitting',
          label: 'chooser_sitter',
          hint: 'chooser_sitter_hint',
          icon: CatMapIcons.SITTER,
          color: '#5E35B1',
          route: 'sitting',
        },
      ];
  return (
    <Sheet title={t(sitting ? 'chooser_sitter' : 'post_what')} onClose={onClose}>
      <p className="muted">{t(sitting ? 'chooser_sitter_hint' : 'post_what_hint')}</p>
      <div className="choice-grid">
        {choices.map((c) => (
          <button
            className="choice-tile"
            key={c.key}
            style={{ '--accent': c.color } as React.CSSProperties}
            onClick={() => {
              if (c.route === 'sitting') {
                setSitting(true);
                return;
              }
              onClose();
              navigate(c.route);
            }}
          >
            <Icon name={c.icon} size={32} />
            <strong>{t(c.label)}</strong>
            <small>{t(c.hint)}</small>
          </button>
        ))}
      </div>
      {sitting && (
        <button className="text-button" onClick={() => setSitting(false)}>
          {t('back')}
        </button>
      )}
    </Sheet>
  );
}
