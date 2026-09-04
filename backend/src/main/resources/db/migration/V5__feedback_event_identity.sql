-- Apply manually before deploying: this project does not run Flyway/Liquibase.
-- Nullable additions deliberately leave historical evidence unverified.
alter table taste_feedback add column if not exists identity_verified boolean;
alter table taste_feedback add column if not exists source varchar(255);
alter table taste_feedback add column if not exists context_id varchar(255);
alter table taste_feedback add column if not exists event_key varchar(255);
create unique index if not exists uk_feedback_user_event on taste_feedback(user_id, event_key);
-- Hibernate-generated PostgreSQL enum checks must permit the additive action values.
alter table taste_feedback drop constraint if exists taste_feedback_action_check;
alter table taste_feedback add constraint taste_feedback_action_check check (action in
('SAVED','UNSAVED','ADDED_TO_TRIP','REMOVED_FROM_TRIP','REMOVED','VISITED','SKIPPED',
 'NOT_INTERESTED','TOO_EXPENSIVE','TOO_FAR','ALREADY_VISITED','REPLACED'));
