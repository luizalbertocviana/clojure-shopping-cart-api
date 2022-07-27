create user test with password 'test' createdb;

create database test
       with
       owner = test
       encoding = 'UTF8'
       lc_collate = 'en_US.utf8'
       lc_ctype = 'en_US.utf8'
       tablespace = pg_default
       connection limit = -1;
