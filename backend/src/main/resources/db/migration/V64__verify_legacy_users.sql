-- Legacy users existed before email verification was introduced.
-- They have no verification token, so mark them as verified.
UPDATE users
SET email_verified = TRUE
WHERE email_verified = FALSE
  AND email_verification_token IS NULL;
