# Google Play Data Safety worksheet

Use this as a draft; answer the Play Console form according to the final production configuration and hosted privacy policy.

| Category | Collected/shared | Purpose | Handling |
|---|---|---|---|
| User IDs | Collected | Account, quota, security | Supabase; linked to user; deletable |
| Email address | Collected only after Google link | Account management/restore | Supabase/Google; deletable |
| Photos | Transiently processed; shared with OpenAI for cloud evaluation | App functionality/fraud prevention | Private temporary Supabase object; deleted after evaluation; TLS; `store:false` |
| Purchase history | Collected | Subscription and restore | Verified with Google Play; server-only |
| App activity / installed apps | Processed on device | Blocking functionality | Selected launcher apps stay local |
| Web browsing / DNS names | Processed | Website filtering | URL local; DNS sent to Cloudflare; no UtilLock history |
| Diagnostics | Not implemented in MVP | — | Do not mark collected unless analytics/crash SDK is later added |

Security statements supported by the implementation: encrypted in transit, no data sale, in-app deletion request, no advertising, no backup of local tokens/rules.

The photo may count as collected even if ephemeral because it leaves the device. Do not mark it “not collected” solely because UtilLock does not persist it.

