# Stall feed format

The app reads this shape from both `app/src/main/assets/stalls.json` and any
remote feed URL set in Settings. The remote feed must be served over `https`.

```json
{
  "version": 1,
  "source": "my curated Penang list",
  "updatedAt": "2026-09-15",
  "stalls": [
    {
      "id": "gurney-drive",
      "name": "Gurney Drive Hawker Centre",
      "category": "Hawker centre",
      "area": "Gurney Drive",
      "lat": 5.4377,
      "lng": 100.3093,
      "notes": "Big seafront food court.",
      "facebookQuery": "Gurney Drive hawker"
    }
  ]
}
```

## Fields

| Field | Required | Notes |
| --- | --- | --- |
| `id` | yes | Unique and stable; used as the list key. |
| `name` | yes | Shown as the card title. |
| `lat` / `lng` | yes | Decimal degrees. Rows outside ±90 / ±180 are dropped. |
| `category` | no | Defaults to `"Food"`. |
| `area` | no | Neighbourhood, shown next to the category. |
| `notes` | no | One or two lines of description. |
| `facebookQuery` | no | What to search the Penang Foodie page for. Defaults to `name`. |
| `source` | no | Shown in the summary card so you can tell which feed is live. |
| `updatedAt` | no | Free-form date string. |

## Behaviour

- A row missing `id`, `name`, or valid coordinates is **skipped**; the rest of
  the feed still loads. One malformed entry cannot empty the list.
- If the remote fetch fails, times out (10 s connect / 15 s read), or returns
  zero usable stalls, the app falls back to the bundled catalogue.
- The feed is cached in memory; pull **Refresh** in the app to re-fetch.
