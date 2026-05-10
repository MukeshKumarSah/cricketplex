-- Convert stored MinIO URLs to object keys (strip MinIO URL prefix)
UPDATE users
SET profile_pic_url = SUBSTRING(profile_pic_url FROM LENGTH('http://localhost:9000/cricketplex/') + 1)
WHERE profile_pic_url LIKE 'http://localhost:9000/cricketplex/%';

UPDATE teams
SET team_profile_pic_url = SUBSTRING(team_profile_pic_url FROM LENGTH('http://localhost:9000/cricketplex/') + 1)
WHERE team_profile_pic_url LIKE 'http://localhost:9000/cricketplex/%';
