import { doc, setDoc, deleteDoc } from 'firebase/firestore';
import { col, ref, listQuery } from './Firestore';
export interface HelpContact {
  id: string;
  name: string;
  kind: string;
  phone: string;
  address: string;
  website: string;
  note: string;
  demo: boolean;
  updatedAt: number;
}
export const HelpContactDefaults: HelpContact = {
  id: '',
  name: '',
  kind: 'vet',
  phone: '',
  address: '',
  website: '',
  note: '',
  demo: false,
  updatedAt: 0,
};
export const HelpContactFromDoc = (id: string, data: Record<string, unknown>): HelpContact =>
  ({ ...HelpContactDefaults, ...data, id }) as HelpContact;
export const HelpDirectoryRepository = {
  all: () => listQuery('helpDirectory'),
  save: async (p: HelpContact) => {
    const { id, ...data } = p;
    await setDoc(id ? ref(`helpDirectory/${id}`) : doc(col('helpDirectory')), {
      ...data,
      name: p.name.trim(),
      updatedAt: Date.now(),
    });
  },
  delete: (p: HelpContact) => deleteDoc(ref(`helpDirectory/${p.id}`)),
};
