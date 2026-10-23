"""Current document-operation scenarios; no production usage or dollar forecast."""
import argparse
import json

def ledger(installs=500, enabled_fraction=1.0, polls=96, worker_posts=0, worker_matches=0, rule_reads=0):
    if not 0 <= enabled_fraction <= 1: raise ValueError('enabled_fraction must be between 0 and 1')
    if any(v < 0 for v in (installs,polls,worker_posts,worker_matches,rule_reads)): raise ValueError('Negative workloads are invalid')
    return {
        'kind':'assumed document operations; excludes unmeasured billing components',
        'background_empty_query_floor':round(installs*enabled_fraction*polls),
        'map_cold_document_upper_bound_per_session':6*2*50,
        'worker_startup_source_documents':worker_posts+worker_matches,
        'worker_embedding_warm_reads_upper_bound':min(worker_posts,2048)*2,
        'worker_orphan_sweep_reads_upper_bound_per_nonempty_page':50,
        'worker_orphan_sweep_max_pages_per_day':96,
        'maintenance_empty_query_floor_per_idle_day_if_enabled':2016,
        'rule_dependent_reads_supplied_by_measurement':rule_reads,
        'writes_per_message_with_alert':5,
        'writes_per_uploaded_photo_with_thumbnail_and_lease':3,
        'writes_per_delivered_lost_alert_including_cursor':3,
        'initial_history_document_limit':50,
    }

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--installs',type=int,default=500)
    parser.add_argument('--enabled-fraction',type=float,default=1)
    parser.add_argument('--polls',type=int,default=96)
    parser.add_argument('--worker-posts',type=int,default=0)
    parser.add_argument('--worker-matches',type=int,default=0)
    parser.add_argument('--rule-reads',type=int,default=0)
    print(json.dumps(ledger(**vars(parser.parse_args())),indent=2))
