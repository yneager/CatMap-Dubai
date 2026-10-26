"""Portable credential-free suite. Real-model/Firestore integration remains explicit."""
import runpy
import unittest
from pathlib import Path
HERE=Path(__file__).resolve().parent
suite=unittest.TestSuite()
for name in ('test_job_protocol.py','test_job_sweep.py','test_recovery_budget.py','test_work_queue.py','test_migration.py','test_maintenance.py','test_review.py'):
    suite.addTests(unittest.defaultTestLoader.discover(str(HERE),pattern=name))
result=unittest.TextTestRunner(verbosity=2).run(suite)
if not result.wasSuccessful():raise SystemExit(1)
for name in ('test_geocells.py','test_searchterms.py','test_matching.py','test_fingerprint.py'):
    runpy.run_path(str(HERE/name),run_name='__main__')
