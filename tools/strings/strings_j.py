# Lost-to-reunited workflow: match suggestions, review, chat cards, reunion (Oct 2026).
# Arabic written by Claude; marked AR-REVIEW so a native speaker can check the wording.
S = [
('tier_strong','Strong possible match','تطابق محتمل قوي'), # AR-REVIEW
('tier_possible','Possible match','تطابق محتمل'), # AR-REVIEW
('tier_weak','Weak match','تطابق ضعيف'), # AR-REVIEW
('tier_none','No reliable match','لا يوجد تطابق موثوق'), # AR-REVIEW
('resolution_catmap','Through a sighting on CatMap','من خلال مشاهدة على CatMap'), # AR-REVIEW
('resolution_home','They came home on their own','عاد إلى البيت بنفسه'), # AR-REVIEW
('resolution_other','Another way','بطريقة أخرى'), # AR-REVIEW
('your_pet','your pet','حيوانك الأليف'), # AR-REVIEW
('n_match_found_title','Possible sighting of %1$s!','مشاهدة محتملة لـ %1$s!'), # AR-REVIEW
('n_match_found_body','Someone spotted a similar-looking %1$s near %2$s. Review the photos to see if it could be your pet.','شاهد أحدهم %1$s يشبهه بالقرب من %2$s. راجع الصور لترى إن كان حيوانك الأليف.'), # AR-REVIEW
('n_match_reporter_title','Your sighting may help find %1$s','قد تساعد مشاهدتك في العثور على %1$s'), # AR-REVIEW
('n_match_reporter_body','The %1$s you saw near %2$s looks like a pet reported lost near %3$s. Have a look.','يشبه %1$s الذي رأيته بالقرب من %2$s حيواناً أليفاً أُبلغ عن فقدانه بالقرب من %3$s. ألقِ نظرة.'), # AR-REVIEW
('n_match_info_title','%1$s\'s owner has a question','لدى صاحب %1$s سؤال'), # AR-REVIEW
('n_match_info_body','@%1$s would like more details about your sighting. Open the chat to reply.','يرغب @%1$s في معرفة المزيد عن مشاهدتك. افتح المحادثة للرد.'), # AR-REVIEW
('n_match_reunited_title','%1$s is home!','%1$s عاد إلى البيت!'), # AR-REVIEW
('n_match_reunited_body','The owner marked them as reunited after your sighting. Thank you for helping.','أكّد المالك عودته بعد مشاهدتك. شكراً لمساعدتك.'), # AR-REVIEW
('matches_checking','Looking for matching sightings…','جارٍ البحث عن مشاهدات مطابقة…'), # AR-REVIEW
('matches_checking_sighting','Checking whether this matches a lost pet…','جارٍ التحقق مما إذا كان يطابق حيواناً مفقوداً…'), # AR-REVIEW
('matches_none_lost',"No matching sightings yet. You'll get an alert when someone posts one.",'لا توجد مشاهدات مطابقة بعد. سيصلك تنبيه عندما ينشر أحدهم مشاهدة.'), # AR-REVIEW
('matches_none_sighting','No lost report matches this yet.','لا يوجد بلاغ فقدان يطابق هذا بعد.'), # AR-REVIEW
('matches_offline',"The AI server was offline, so these were compared by colour on a phone. The AI will check them again when it's back.",'كان خادم الذكاء الاصطناعي غير متصل، لذا قورنت هذه حسب اللون على الهاتف. سيعيد الذكاء الاصطناعي فحصها عند عودته.'), # AR-REVIEW
('show_weak_matches','Show %1$d weak matches','عرض التطابقات الضعيفة (%1$d)'), # AR-REVIEW
('match_found_headline','Possible match: %1$s','تطابق محتمل: %1$s'), # AR-REVIEW
('match_lost_near','lost near %1$s','فُقد بالقرب من %1$s'), # AR-REVIEW
('match_seen_near','Seen near %1$s','شوهد بالقرب من %1$s'), # AR-REVIEW
('match_distance_apart','%1$s apart','على بُعد %1$s'), # AR-REVIEW
('match_review','Compare the photos','قارن الصور'), # AR-REVIEW
('match_review_title','Compare the photos','قارن الصور'), # AR-REVIEW
('match_lost_photo','Lost report','بلاغ الفقدان'), # AR-REVIEW
('match_sighting_photo','Sighting','المشاهدة'), # AR-REVIEW
('match_why','Why it was suggested','سبب الاقتراح'), # AR-REVIEW
('match_not_proof','This is a suggestion, not a confirmation. Only the owner can decide.','هذا اقتراح وليس تأكيداً. القرار للمالك وحده.'), # AR-REVIEW
('match_compare_features','Check these before deciding: ear tips, face markings, tail, collar and any distinctive marks.','تحقّق من هذه قبل أن تقرر: أطراف الأذنين، وعلامات الوجه، والذيل، والطوق، وأي علامات مميزة.'), # AR-REVIEW
('match_owner_features','Distinctive features in the lost report: %1$s','العلامات المميزة في بلاغ الفقدان: %1$s'), # AR-REVIEW
('match_compare_details','Details side by side','التفاصيل جنباً إلى جنب'), # AR-REVIEW
('match_when','When','متى'), # AR-REVIEW
('colour','Colour','اللون'), # AR-REVIEW
('open_photo','Open photo','فتح الصورة'), # AR-REVIEW
('match_ai_label','Compared by the AI recognition model','قورنت بنموذج التعرّف بالذكاء الاصطناعي'), # AR-REVIEW
('match_device_label','Compared by colour on a phone (AI server offline)','قورنت حسب اللون على الهاتف (خادم الذكاء الاصطناعي غير متصل)'), # AR-REVIEW
('reason_photo_very_similar','The photos look very similar to our AI model.','تبدو الصور متشابهة جداً لنموذج الذكاء الاصطناعي.'), # AR-REVIEW
('reason_photo_similar','The photos look similar to our AI model.','تبدو الصور متشابهة لنموذج الذكاء الاصطناعي.'), # AR-REVIEW
('reason_photo_somewhat_similar','Some resemblance in the photos, but many animals look alike.','هناك بعض الشبه في الصور، لكن حيوانات كثيرة تتشابه.'), # AR-REVIEW
('reason_photo_different',"The photos don't look much alike.",'الصور لا تتشابه كثيراً.'), # AR-REVIEW
('reason_colour_only',"Only the colours were compared, which can't tell look-alikes apart.",'قورنت الألوان فقط، وهي لا تميّز بين الحيوانات المتشابهة.'), # AR-REVIEW
('reason_no_photo','One of the posts has no photo, so only the details were compared.','أحد المنشورين بلا صورة، لذا قورنت التفاصيل فقط.'), # AR-REVIEW
('reason_same_colour_words','Both descriptions mention the same colours.','يذكر الوصفان الألوان نفسها.'), # AR-REVIEW
('reason_near','Seen close to where it went missing (%1$s apart).','شوهد قريباً من مكان فقدانه (على بُعد %1$s).'), # AR-REVIEW
('reason_in_range','Seen %1$s away, within reach for a lost pet.','شوهد على بُعد %1$s، ضمن المسافة التي قد يقطعها حيوان مفقود.'), # AR-REVIEW
('reason_far','Seen %1$s away, farther than lost pets usually go.','شوهد على بُعد %1$s، أبعد مما تذهب إليه الحيوانات المفقودة عادةً.'), # AR-REVIEW
('reason_recent','Seen %1$s after it went missing.','شوهد بعد %1$s من فقدانه.'), # AR-REVIEW
('reason_long_after','Seen %1$s after it went missing, a long time.','شوهد بعد %1$s من فقدانه، وهي مدة طويلة.'), # AR-REVIEW
('reason_before_lost','Seen before the day it went missing. Check the dates.','شوهد قبل يوم فقدانه. تحقّق من التواريخ.'), # AR-REVIEW
('reason_like_rejected',"Looks like another sighting the owner said wasn't their pet.",'يشبه مشاهدة أخرى قال المالك إنها ليست لحيوانه.'), # AR-REVIEW
('reason_reporter_rejected',"The person who saw it thinks it's a different animal.",'يعتقد من شاهده أنه حيوان مختلف.'), # AR-REVIEW
('reason_reporter_linked','The person who saw it linked it to this report.','ربط من شاهده مشاهدته بهذا البلاغ.'), # AR-REVIEW
('days_less_than_one','less than a day','أقل من يوم'), # AR-REVIEW
('days_n','%1$d days','%1$d يوم'), # AR-REVIEW
('match_action_maybe','This might be my animal','قد يكون هذا حيواني'), # AR-REVIEW
('match_action_not_mine','This is not my animal','هذا ليس حيواني'), # AR-REVIEW
('match_action_info','Request more information','اطلب مزيداً من المعلومات'), # AR-REVIEW
('match_action_same',"I think it's the same animal",'أعتقد أنه الحيوان نفسه'), # AR-REVIEW
('match_action_not_same',"It's a different animal",'إنه حيوان مختلف'), # AR-REVIEW
('match_message','Message about this match','راسِل بشأن هذا التطابق'), # AR-REVIEW
('match_view_lost','View lost report','عرض بلاغ الفقدان'), # AR-REVIEW
('match_view_sighting','View sighting','عرض المشاهدة'), # AR-REVIEW
('match_undo','Undo my answer','تراجع عن إجابتي'), # AR-REVIEW
('match_rejected_done',"Got it. This sighting won't be suggested again.",'حسناً. لن نقترح هذه المشاهدة مرة أخرى.'), # AR-REVIEW
('match_told_owner','Thanks! The owner has been told.','شكراً! تم إبلاغ المالك.'), # AR-REVIEW
('match_you_said_maybe','You said this might be your animal.','قلت إن هذا قد يكون حيوانك.'), # AR-REVIEW
('match_you_said_no',"You said this isn't your animal. It won't be suggested again.",'قلت إن هذا ليس حيوانك. لن نقترحه مرة أخرى.'), # AR-REVIEW
('match_reporter_said_same',"The person who saw it thinks it's the same animal.",'يعتقد من شاهده أنه الحيوان نفسه.'), # AR-REVIEW
('match_owner_said_maybe','The owner thinks this might be their animal.','يعتقد المالك أن هذا قد يكون حيوانه.'), # AR-REVIEW
('match_owner_said_no','The owner says this is not their animal.','يقول المالك إن هذا ليس حيوانه.'), # AR-REVIEW
('match_owner_wants_info','The owner asked you for more information.','طلب منك المالك مزيداً من المعلومات.'), # AR-REVIEW
('match_reunited_badge','Reunited thanks to this sighting','عاد إلى أهله بفضل هذه المشاهدة'), # AR-REVIEW
('match_unavailable','This match is no longer available.','لم يعد هذا التطابق متاحاً.'), # AR-REVIEW
('match_verify','Mark the reunion as checked by a moderator','تأكيد لمّ الشمل من قِبل مشرف'), # AR-REVIEW
('match_suggested_at','Suggested %1$s','اقتُرح في %1$s'), # AR-REVIEW
('match_card_chat','Possible match','تطابق محتمل'), # AR-REVIEW
('match_card_open','Compare the photos','قارن الصور'), # AR-REVIEW
('link_my_sighting','I posted a sighting of this animal','نشرتُ مشاهدة لهذا الحيوان'), # AR-REVIEW
('link_my_sighting_body','Choose your sighting. The owner will be told and can compare the photos.','اختر مشاهدتك. سيُبلَّغ المالك ويمكنه مقارنة الصور.'), # AR-REVIEW
('reunite_title','Mark %1$s as reunited','تأكيد عودة %1$s'), # AR-REVIEW
('reunite_how','How did you find them?','كيف وجدته؟'), # AR-REVIEW
('reunite_which','Which sighting helped?','أي مشاهدة ساعدت؟'), # AR-REVIEW
('reunite_confirm','Mark as reunited','تأكيد العودة'), # AR-REVIEW
('reunited_title','%1$s is home!','%1$s عاد إلى البيت!'), # AR-REVIEW
('reunited_body','The report now shows Reunited on the map, alerts have stopped, and anyone whose sighting helped has been thanked.','يظهر البلاغ الآن بحالة «عاد إلى أهله» على الخريطة، وتوقفت التنبيهات، وتم شكر من ساعدت مشاهدته.'), # AR-REVIEW
('reunited_share','Share the good news','شارك الخبر السار'), # AR-REVIEW
('reunited_share_text','Good news: %1$s is back home, thanks to the CatMap Dubai community.','خبر سار: %1$s عاد إلى البيت بفضل مجتمع CatMap دبي.'), # AR-REVIEW
('reunited_banner','Reunited on %1$s','عاد إلى أهله في %1$s'), # AR-REVIEW
('reunited_via_catmap','Found through a sighting on CatMap','وُجد من خلال مشاهدة على CatMap'), # AR-REVIEW
('reunited_home','Came home on their own','عاد إلى البيت بنفسه'), # AR-REVIEW
('reunion_owner_reported','Reported by the owner','بإفادة المالك'), # AR-REVIEW
('reunion_confirmed','Confirmed by the person who saw them','أكّده من شاهده'), # AR-REVIEW
('reunion_verified','Checked by a moderator','تحقّق منه مشرف'), # AR-REVIEW
('report_history','Report history','سجل البلاغ'), # AR-REVIEW
('history_reported_lost','Reported lost','أُبلغ عن فقدانه'), # AR-REVIEW
('history_posted','Posted','نُشر'), # AR-REVIEW
('history_update','Update from %1$s','تحديث من %1$s'), # AR-REVIEW
('history_match','Possible match suggested (%1$s)','اقتُرح تطابق محتمل (%1$s)'), # AR-REVIEW
('history_closed','Marked %1$s','وُضعت الحالة: %1$s'), # AR-REVIEW
('analytics_matches','Match suggestions','اقتراحات التطابق'), # AR-REVIEW
('analytics_matches_body','%1$d suggested (strong %2$d, possible %3$d, weak %4$d). Owners said "might be mine" %5$d times and "not mine" %6$d times.','اقتُرح %1$d (قوي %2$d، محتمل %3$d، ضعيف %4$d). قال المالكون «قد يكون حيواني» %5$d مرة و«ليس حيواني» %6$d مرة.'), # AR-REVIEW
('analytics_reunions_body','Reunions: %1$d, through a CatMap sighting: %2$d (confirmed by the reporter: %3$d, checked by a moderator: %4$d).','حالات العودة: %1$d، منها عبر مشاهدة على CatMap: %2$d (أكّدها المُبلِّغ: %3$d، تحقّق منها مشرف: %4$d).'), # AR-REVIEW
]
