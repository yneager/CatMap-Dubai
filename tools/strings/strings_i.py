S = [
('help_kind_vet','Vet','Vet'), # TODO-AR
('help_kind_shelter','Shelter','Shelter'), # TODO-AR
('help_kind_emergency','Emergency','Emergency'), # TODO-AR
('help_kind_other','Other','Other'), # TODO-AR
('demo_entry_label','Demo','Demo'), # TODO-AR
('demo_contact','Demo entry','Demo entry'), # TODO-AR
('distance_from_map_center','Distances from the map center until GPS is available.','Distances from the map center until GPS is available.'), # TODO-AR
('metadata_match','Similar type, location and time','Similar type, location and time'), # TODO-AR
('deleted_member','Deleted member','Deleted member'), # TODO-AR
('unknown','Unknown','Unknown'), # TODO-AR
('microchip','Microchip','Microchip'), # TODO-AR
('microchip_privacy','Record only yes/no/unknown. Never publish a microchip number.','Record only yes/no/unknown. Never publish a microchip number.'), # TODO-AR
('release_notes_unavailable','Release notes are unavailable for this version.','Release notes are unavailable for this version.'), # TODO-AR
('whats_new_version',"What's new in %1$s","What's new in %1$s"), # TODO-AR
('admin_analytics','Community analytics','Community analytics'), # TODO-AR
('weekly_posts','Posts per week','Posts per week'), # TODO-AR
('weekly_members','New members per week','New members per week'), # TODO-AR
('analytics_axis','Horizontal: week · Vertical: count','Horizontal: week · Vertical: count'), # TODO-AR
('analytics_accessibility','Weekly posts: %1$s. Weekly new members: %2$s.','Weekly posts: %1$s. Weekly new members: %2$s.'), # TODO-AR
('reunion_rate','Reunion rate: %1$s%%','Reunion rate: %1$s%%'), # TODO-AR
('reunion_rate_basis','Lost/found posts created in the last eight weeks that are currently marked reunited.','Lost/found posts created in the last eight weeks that are currently marked reunited.'), # TODO-AR
('quiet_hours','Quiet hours','Quiet hours'), # TODO-AR
('quiet_start','Start','Start'), # TODO-AR
('quiet_end','End','End'), # TODO-AR
('quiet_hours_note','Uses your device time. Alerts remain in your inbox and are not replayed later. Equal start/end times silence alerts all day.','Uses your device time. Alerts remain in your inbox and are not replayed later. Equal start/end times silence alerts all day.'), # TODO-AR
('download_my_data','Download my data','Download my data'), # TODO-AR
('name','Name','Name'), # TODO-AR
('verified_only','Verified only','Verified only'), # TODO-AR
('verified_sitter','Verified sitter','Verified sitter'), # TODO-AR
('verify_sitter','Verify sitter','Verify sitter'), # TODO-AR
('remove_verification','Remove verification','Remove verification'), # TODO-AR
('help_directory','Help directory','Help directory'), # TODO-AR
('no_help_contacts','No contacts have been added yet.','No contacts have been added yet.'), # TODO-AR
('help_phone','Phone','Phone'), # TODO-AR
('help_address','Address','Address'), # TODO-AR
('help_website','Website','Website'), # TODO-AR
('show_map_view','Map view','Map view'), # TODO-AR
('show_list_view','List view','List view'), # TODO-AR
('possible_post_matches','Possible matches','Possible matches'), # TODO-AR
('match_confidence','Match score: %1$d%%','Match score: %1$d%%'), # TODO-AR
('stale_post_title','Still missing?','Still missing?'), # TODO-AR
('stale_post_body','Please update %1$s: mark reunited/resolved or confirm you are still looking.','Please update %1$s: mark reunited/resolved or confirm you are still looking.'), # TODO-AR
('include_older_posts','Include older posts','Include older posts'), # TODO-AR
('still_looking','Still looking','Still looking'), # TODO-AR
('draft_available','Continue your saved post?','Continue your saved post?'), # TODO-AR
('continue_draft','Continue draft','Continue draft'), # TODO-AR
('discard_draft','Discard','Discard'), # TODO-AR
('demo_accounts','Demo accounts','Demo accounts'), # TODO-AR
('demo_accounts_body','Switch between the admin and the four demo users to show the app. Logging out forgets the demo password on this phone.','Switch between the admin and the four demo users to show the app. Logging out forgets the demo password on this phone.'), # TODO-AR
('demo_switching','Switching account...','Switching account...'), # TODO-AR
('err_demo_sign_in_again','The demo password has changed. Log out and sign in as admin again.','The demo password has changed. Log out and sign in as admin again.'), # TODO-AR
('err_demo_missing',"That demo account doesn't exist yet. Run tools/demo/create-demo-accounts.mjs first.","That demo account doesn't exist yet. Run tools/demo/create-demo-accounts.mjs first."), # TODO-AR
('match_strong','Strong match','Strong match'), # TODO-AR
('match_possible','Possible match','Possible match'), # TODO-AR
('match_weak','Weak match','Weak match'), # TODO-AR
('match_check_marks','Similar-looking cats can score high. Compare ear tips, face markings and the tail before confirming.','Similar-looking cats can score high. Compare ear tips, face markings and the tail before confirming.'), # TODO-AR
('several_subjects_hint','More than one animal or person was found in a photo. Only the largest one was analysed; crop the photo for better matches.','More than one animal or person was found in a photo. Only the largest one was analysed; crop the photo for better matches.'), # TODO-AR
('recognition_test','Recognition test','Recognition test'), # TODO-AR
('recognition_test_intro','Measures how well the app tells individual cats apart, using its own analyser. Put photos on this phone in one folder with a sub-folder per cat (for example CatTest/Zafran/1.jpg), with at least two photos of each cat. Nothing is uploaded.','Measures how well the app tells individual cats apart, using its own analyser. Put photos on this phone in one folder with a sub-folder per cat (for example CatTest/Zafran/1.jpg), with at least two photos of each cat. Nothing is uploaded.'), # TODO-AR
('recognition_choose_folder','Choose photo folder','Choose photo folder'), # TODO-AR
('recognition_analysing','Analysing photo %1$d of %2$d...','Analysing photo %1$d of %2$d...'), # TODO-AR
('recognition_too_few','Need at least two cats, and two photos of a cat, in sub-folders of the chosen folder.','Need at least two cats, and two photos of a cat, in sub-folders of the chosen folder.'), # TODO-AR
('recognition_dataset','%1$d cats, %2$d photos','%1$d cats, %2$d photos'), # TODO-AR
('recognition_top1','Right cat ranked first','Right cat ranked first'), # TODO-AR
('recognition_top3','Right cat in the top three','Right cat in the top three'), # TODO-AR
('recognition_top1_best_photo','First, comparing photo by photo','First, comparing photo by photo'), # TODO-AR
('recognition_false_suggestion','Unknown cat still got a suggestion','Unknown cat still got a suggestion'), # TODO-AR
('recognition_false_strong','Unknown cat got a "strong" match','Unknown cat got a "strong" match'), # TODO-AR
('recognition_time','Average analysis time: %1$d ms per photo','Average analysis time: %1$d ms per photo'), # TODO-AR
('recognition_share','Share full report','Share full report'), # TODO-AR
('sitting_offer','Offer pet sitting','Offer pet sitting'), # TODO-AR
('sitting_offer_hint','Become a sitter in your area','Become a sitter in your area'), # TODO-AR
('sitting_request','Request pet sitting','Request pet sitting'), # TODO-AR
('sitting_request_hint','Find a sitter near you and book','Find a sitter near you and book'), # TODO-AR
('same_animal_title','Already on the map?','Already on the map?'), # TODO-AR
('same_animal_hint_ai',"Our AI compared your photo with street animals posted nearby. If it's the same animal, your photo is added to that post instead of creating a second one.","Our AI compared your photo with street animals posted nearby. If it's the same animal, your photo is added to that post instead of creating a second one."), # TODO-AR
('same_animal_hint_local',"Street animals posted nearby look similar (checked on this phone; the AI server isn't reachable). If it's the same animal, your photo is added to that post.","Street animals posted nearby look similar (checked on this phone; the AI server isn't reachable). If it's the same animal, your photo is added to that post."), # TODO-AR
('same_animal_checking','Checking for the same animal nearby...','Checking for the same animal nearby...'), # TODO-AR
('same_animal_likely','Likely the same','Likely the same'), # TODO-AR
('same_animal_maybe','Might be the same','Might be the same'), # TODO-AR
('same_animal_yes','Same animal','Same animal'), # TODO-AR
('same_animal_no','Different animal','Different animal'), # TODO-AR
('same_animal_added','Added your photo to the existing post','Added your photo to the existing post'), # TODO-AR
('same_animal_add','Add to that post','Add to that post'), # TODO-AR
('same_animal_post_anyway','Post anyway','Post anyway'), # TODO-AR
('same_animal_publish_body','Our AI thinks this is the same animal as "%1$s", already on the map. Add your photo to that post instead of making a second one?','Our AI thinks this is the same animal as "%1$s", already on the map. Add your photo to that post instead of making a second one?'), # TODO-AR
('ai_server','AI server','AI server'), # TODO-AR
('ai_server_intro',"Street-animal posts are checked against nearby posts by the recognition model (pet-recognition-base) running on the owner's PC. With the phone plugged in by USB and tools/ai-server/start.ps1 running, leave this blank.","Street-animal posts are checked against nearby posts by the recognition model (pet-recognition-base) running on the owner's PC. With the phone plugged in by USB and tools/ai-server/start.ps1 running, leave this blank."), # TODO-AR
('ai_server_url','Server address (blank = USB, http://127.0.0.1:8765)','Server address (blank = USB, http://127.0.0.1:8765)'), # TODO-AR
('ai_server_test','Test connection','Test connection'), # TODO-AR
('ai_server_ok','Connected: %1$s','Connected: %1$s'), # TODO-AR
('ai_server_down',"Can't reach the AI server. Is start.ps1 running and the phone plugged in?","Can't reach the AI server. Is start.ps1 running and the phone plugged in?"), # TODO-AR
('recognition_ai_top1','AI model: right cat ranked first','AI model: right cat ranked first'), # TODO-AR
('recognition_ai_unavailable','AI server not reachable, so only the on-device method was tested.','AI server not reachable, so only the on-device method was tested.'), # TODO-AR
]
