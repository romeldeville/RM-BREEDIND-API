ALTER TABLE users ALTER COLUMN email TYPE varchar(320);
ALTER TABLE invitations ALTER COLUMN email TYPE varchar(320);

CREATE UNIQUE INDEX IF NOT EXISTS users_email_lower_uidx ON users (lower(email));
CREATE UNIQUE INDEX IF NOT EXISTS invitations_email_lower_uidx ON invitations (lower(email));
