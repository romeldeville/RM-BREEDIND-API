CREATE TABLE event_publication (
    id uuid NOT NULL PRIMARY KEY,
    listener_id text NOT NULL,
    event_type text NOT NULL,
    serialized_event text NOT NULL,
    publication_date timestamptz NOT NULL,
    completion_date timestamptz,
    last_resubmission_date timestamptz,
    completion_attempts integer NOT NULL DEFAULT 0,
    status varchar(255)
);

CREATE INDEX idx_event_publication_completion_date ON event_publication (completion_date);
CREATE INDEX idx_event_publication_status ON event_publication (status);

CREATE TABLE event_publication_archive (
    id uuid NOT NULL PRIMARY KEY,
    listener_id text NOT NULL,
    event_type text NOT NULL,
    serialized_event text NOT NULL,
    publication_date timestamptz NOT NULL,
    completion_date timestamptz,
    last_resubmission_date timestamptz,
    completion_attempts integer NOT NULL DEFAULT 0,
    status varchar(255)
);

CREATE INDEX idx_event_publication_archive_completion_date
    ON event_publication_archive (completion_date);
