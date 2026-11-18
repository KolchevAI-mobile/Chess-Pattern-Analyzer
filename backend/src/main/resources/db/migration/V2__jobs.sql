-- M2: очередь заданий на импорт и разбор.
-- Воркер забирает задание через FOR UPDATE SKIP LOCKED — без Redis и без дублей.

create table jobs (
    id uuid primary key,
    type text not null,
    status text not null,
    player text not null,
    perf_type text not null default 'rapid',
    limit_games integer not null default 20,
    total_games integer not null default 0,
    processed_games integer not null default 0,
    error text,
    created_at timestamptz not null default now(),
    started_at timestamptz,
    finished_at timestamptz
);

create index ix_jobs_status on jobs (status, created_at);
create index ix_jobs_player on jobs (player, created_at desc);
