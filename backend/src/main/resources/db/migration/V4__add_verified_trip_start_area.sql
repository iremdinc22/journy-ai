-- Optional identity only; historical starting_area strings remain unchanged.
alter table trips add column if not exists starting_area_id varchar(255);
alter table trips add column if not exists starting_area_type varchar(255);
alter table trips add column if not exists starting_area_latitude double precision;
alter table trips add column if not exists starting_area_longitude double precision;
alter table trips add column if not exists starting_area_source varchar(255);
alter table trips add column if not exists starting_area_provider_place_id varchar(255);
