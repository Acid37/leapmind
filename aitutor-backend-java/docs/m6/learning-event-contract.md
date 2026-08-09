# Learning event contract

`LearningEventCommand` is a stable envelope: `eventId`, `subjectUserId`, `occurredAt`, optional `sessionId`, `KnowledgePointRef`, required `traceId`, and one sealed `LearningEventPayload`. IDs use the existing 1--64-character identifier rule; user ID is positive; payload derives `eventType`, source, and schema `1.0`. `EventPublishContext` is a separate trusted-actor context: a USER actor equals the subject; a SERVICE has a typed principal; source module and publish purpose must match the payload. The default policy returns `NOT_CONFIGURED`; only an explicit allow policy followed by the disabled publisher returns `NOT_CONNECTED`.

Each event is restricted to its documented data fields. All textual data (including `ask_doubt.topic`) is scanned for credentials and Chinese national-ID values, sensitive keys are rejected, and canonical `data` remains capped at 16 KiB.

| Event / producer | Trigger | Required `data` | Example |
| --- | --- | --- | --- |
| `answer_question` / M1 | answer saved | `isCorrect,difficulty,timeSpentSec,hintCount` | `{"isCorrect":true,"difficulty":2,"timeSpentSec":10,"hintCount":0}` |
| `finish_practice` / M1 | practice finishes | `questionCount,accuracy,durationSec` | `{"questionCount":10,"accuracy":0.8,"durationSec":600}` |
| `request_explanation` / M2 | explanation requested | `explainId,reasonTag` | `{"explainId":"exp-1","reasonTag":"USER_REQUEST"}` |
| `explanation_feedback` / M2 | feedback submitted | `explainId,feedback,repeatCount` | `{"explainId":"exp-1","feedback":"understood","repeatCount":0}` |
| `weak_point_changed` / M3 | weak point recalculated | `oldScore,newScore,reason` | `{"oldScore":0.2,"newScore":0.4,"reason":"RECALCULATED"}` |
| `lecture_interact` / M4 | lecture interaction | `lectureId,chapterId,action` | `{"lectureId":"lec-1","chapterId":"ch-1","action":"pause"}` |
| `lesson_material_used` / M5 | material consumed | `contentId,materialType,result` | `{"contentId":"mat-1","materialType":"text","result":"completed"}` |
| `ask_doubt` / M7 | learner asks doubt | `topic,confusionTag,isFollowUp` | `{"topic":"why","confusionTag":"concept_unclear","isFollowUp":false}` |
| `mark_reviewed` / M6 | review outcome recorded | `result,timeSpentSec,hintCount` | `{"result":"correct_without_hint","timeSpentSec":30,"hintCount":0}` |
| `preference_changed` / M6 | allowed preference changes | `preferenceKey,preferenceValue` | `{"preferenceKey":"learning_pace","preferenceValue":"slow"}` |

Do not put question text, answer text, conversation content, credentials, tokens, or client profile summaries in data. `kpId` is the engine-envelope identity only. `Resolved(Long)` is known; the default no-mapping resolver returns validated `Unresolved(stableKey)` without hashing or guessing; `None` means the event genuinely has no knowledge point, except `answer_question`, which requires a resolved KP before persistence or engine IO. Event IDs and trace IDs are stable caller-owned identifiers, never randomized by this port. This increment exposes disabled ports only and performs zero persistence or network IO; a real publisher is a separately reviewed follow-up.
