# DreamPipe playlist interchange, version 1

The default `.json` export is a complete, portable library backup. It contains playlist identities,
names, creation/update times (Unix milliseconds), ordered video IDs, titles, authors, thumbnails and
durations. It contains no account state, cookies, expiring media URLs, or downloaded media files.

```json
{
  "format": "dreampipe.playlists",
  "version": 1,
  "playlists": [
    {
      "id": "a23f6164-b2cf-4923-b992-639d9d2ee835",
      "name": "My playlist",
      "createdAt": 1792216800000,
      "updatedAt": 1792216800000,
      "videos": [
        {
          "id": "aaaaaaaaaaa",
          "title": "Example title",
          "author": "Example author",
          "thumbnail": "",
          "durationMs": 123000
        }
      ]
    }
  ]
}
```

Import validates the entire file before changing storage. Unsupported versions, invalid video IDs,
duplicate playlist identities, hostile nesting and oversized archives are rejected. The current
limits are 8 MiB per archive/library, 1,000 playlists, and 50,000 video entries across the library.
Android's document picker controls the source and destination; broad storage access is not requested.

**Add copies** keeps an imported identity when it is new. If that identity already exists, a fresh
identity is generated, leaving the existing playlist untouched. **Merge matching** matches identity
first, then a unique case-insensitive name. Ambiguous matching names create a separate playlist.
Existing order and metadata win; new videos are appended in source order. Source playlists are kept.

An unreadable existing store is not silently overwritten. Importing a valid backup presents an
explicit restore action; recovery keeps a separate exact copy of the unreadable bytes first.

Individual playlists can also be exported as UTF-8 extended M3U with `#PLAYLIST` and `#EXTINF` metadata
and canonical public YouTube links. Import accepts M3U/M3U8 or a plain list of supported YouTube links.
M3U has no stable playlist identity, so an imported M3U receives a new identity. JSON is the lossless
DreamPipe backup format. PipePipe/NewPipe database backup ZIP files are not the same format.

Deleting a local playlist does not delete downloaded files or change a YouTube account playlist.

## Optional cover and manual order (1.0.1)

JSON version 1 accepts optional `thumbnailVideoId` on a playlist. It must name an entry in that
playlist; an absent/removed/unknown entry falls back to the first entry. Rename, merge and copy retain
a valid chosen cover. The field is omitted for default covers. Playlist-array order and video-array
order are the manual order, retained across restarts and JSON export/import. M3U retains entry order
and portable links; use JSON for a complete backup, including the chosen thumbnail and identities.

Merge source removal is optional in the UI. When chosen, the target update and source removal are
committed together; a failed write preserves every original. Manual ordering, covers, deduplication
and the JSON schema remain unchanged. Bulk deletion and entry moves also commit as one transaction.
