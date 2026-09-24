## Fork 1.2.2 - Forge startup fix
+ Preserve generated mixin reference maps across incremental builds and reject JARs with missing mappings.
+ Compile shared Minecraft sources only in the main source set.
+ Fix the missing PlayerListMixin reference map that prevented Forge servers from starting.

## Fork 1.2.1 - Status update recovery
+ Allow slow Discord status updates to finish without a three-second timeout or overlapping requests.
+ Log status update failures, retry at the next scheduled interval, and cancel pending updates on shutdown.
+ Restore status updates after a bot restart.

## Fork 1.2.0 - Discord channel compatibility
+ Fix Discord channel loading when application_id or owner_id is null, while preserving other nullable fields.
+ Log Discord cache initialization failures with their cause instead of an unhandled Reactor error.

## 4.2.6 update
+ 1.21.5 support
+ fix death message being always generic on fabric (thanks to CavanCheeta)
+ add missing `${attachements}` variable in `minecraft_chat_format`
