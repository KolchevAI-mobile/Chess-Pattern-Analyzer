-- Схема M1: партии игрока, их разборы и найденные паттерны.
-- payload/evidence — jsonb: новые признаки (MMMP, ΔS) добавятся без миграций.

create table users (
    id uuid primary key,
    device_token_hash text,
    lichess_nick text,
    created_at timestamptz not null default now()
);

create unique index ux_users_lichess_nick
    on users (lichess_nick)
    where lichess_nick is not null;

create table games (
    id uuid primary key,
    user_id uuid not null references users (id) on delete cascade,
    source text not null,
    external_id text not null,
    pgn text not null,
    sha256 text not null,
    white text not null,
    black text not null,
    result text not null,
    time_control text,
    played_at timestamptz,
    created_at timestamptz not null default now(),
    constraint ux_games_source_external unique (source, external_id)
);

create index ix_games_user on games (user_id, played_at desc);

create table analyses (
    id uuid primary key,
    game_id uuid not null references games (id) on delete cascade,
    engine text not null,
    engine_depth integer not null,
    analysis_version text not null,
    accuracy double precision,
    avg_cp_loss double precision,
    payload jsonb not null,
    created_at timestamptz not null default now(),
    constraint ux_analyses_game_version unique (game_id, analysis_version)
);

create index ix_analyses_game on analyses (game_id);

create table pattern_hits (
    id uuid primary key,
    user_id uuid not null references users (id) on delete cascade,
    code text not null,
    title text not null,
    severity double precision not null,
    confidence double precision not null,
    summary text not null,
    evidence jsonb not null default '[]'::jsonb,
    computed_at timestamptz not null default now()
);

create index ix_pattern_hits_user on pattern_hits (user_id, computed_at desc);
