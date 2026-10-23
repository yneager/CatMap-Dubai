import importlib.util
import sys
import unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent.parent))
from emulator_safety import require_emulator
spec=importlib.util.spec_from_file_location('migration',next((p for p in Path(__file__).resolve().parents if (p / 'tools/migrate/approved-extras.py').is_file()), Path(__file__).resolve().parents[2]/'cat')/'tools/migrate/approved-extras.py')
migration=importlib.util.module_from_spec(spec);spec.loader.exec_module(migration)

class MigrationTests(unittest.TestCase):
    def fixture(self):
        return {'users/alice':{'username':'Alice'},'chats/alice_bob':{'members':['alice','bob'],'unread':{'alice':3}},
            'chats/alice_bob/messages/m':{'senderId':'alice','imageId':'legacy'},
            'images/legacy':{'ownerId':'alice','data':'synthetic'},'images/legacy_t':{'ownerId':'alice','data':'synthetic'},
            'areaFollows/alice':{'lat':25,'lng':55,'radiusKm':3,'updatedAt':1,'neighborhoods':['deira']}}
    def test_private_copy_is_bound_to_message_and_old_bytes_removed(self):
        p=migration.plan(self.fixture());self.assertFalse(p['blockers'])
        msg=p['replacements']['chats/alice_bob/messages/m'];self.assertTrue(msg['imageId'].startswith('c_'))
        self.assertEqual(p['replacements']['images/'+msg['imageId']]['messageId'],'m')
        self.assertIn('images/legacy',p['deletions'])
    def test_shared_public_media_is_retained(self):
        f=self.fixture();f['cats/c']={'photoIds':['legacy']};p=migration.plan(f)
        self.assertFalse(p['deletions']);self.assertEqual(p['replacements']['images/legacy']['audience'],'community')
    def test_missing_thumbnail_blocks_private_rewrite(self):
        f=self.fixture();del f['images/legacy_t'];p=migration.plan(f);self.assertTrue(p['blockers']);self.assertNotIn('chats/alice_bob/messages/m',p['replacements'])
    def test_area_is_private_and_counts_are_rebuilt(self):
        p=migration.plan(self.fixture());self.assertNotIn('lat',p['replacements']['areaFollows/alice'])
        self.assertEqual(p['replacements']['userLimits/alice']['unreadCount'],3)
    def test_conflicts_need_owner_resolution(self):
        f=self.fixture();f['usernames/alice']={'uid':'bob'};f['users/bob']={'username':'alice'}
        self.assertGreaterEqual(len(migration.plan(f)['blockers']),2)
    def test_plan_is_deterministic_and_has_no_apply_mode(self):
        self.assertEqual(migration.plan(self.fixture()),migration.plan(self.fixture()))
    def test_reapplying_migrated_fixture_is_a_noop(self):
        f=self.fixture();first=migration.plan(f)
        for path in first['deletions']:f.pop(path,None)
        f.update(first['replacements']);second=migration.plan(f)
        self.assertFalse(second['blockers']);self.assertFalse(second['replacements']);self.assertFalse(second['deletions'])
    def test_conflicting_destination_never_overwrites_another_photo(self):
        f=self.fixture();first=migration.plan(f)
        image=next(p for p in first['replacements'] if p.startswith('images/c_') and not p.endswith('_t'))
        f[image]={'ownerId':'someone_else','data':'unrelated'};second=migration.plan(f)
        self.assertTrue(second['blockers']);self.assertNotIn(image,second['replacements']);self.assertNotIn('images/legacy',second['deletions'])
    def test_invalid_public_neighborhood_entries_are_blockers(self):
        f=self.fixture();f['areaFollows/alice']['neighborhoods']=['x'*1000]
        self.assertTrue(migration.plan(f)['blockers'])
    def test_emulator_guard_refuses_remote_and_real_projects(self):
        for project,host in [('catmap-dubai','127.0.0.1:8180'),('demo-test','example.com:8180'),('demo-test','localhost'),('demo-test','127.0.0.1.example.com:8180')]:
            with self.assertRaises(ValueError):require_emulator(project,host)
        require_emulator('demo-test','127.0.0.1:8180')

if __name__=='__main__':unittest.main()
