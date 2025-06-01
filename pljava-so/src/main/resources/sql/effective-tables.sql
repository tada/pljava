--
-- use 'import foreign schema' !!
--
-- that said the schema should be this
--

create table jar_code_source (
--  id                 tbd primary key,
    filename           text not null,
    manifest           text
);


create table jar_entry (
--  cs_id              tbd foreign key references jar_code_source(id),
    name               text not null,
    'comment'          text,
    crc                int8,
    creation_time      timestamp(3) without time zone,
    extra              bytea,
    last_access_time   timestamp(3) without time zone,
    last_modified_time timestamp(3) without time zone,
    method             int4,
    size               int8,
    time               int8,
    is_directory       bool,
    is_bytecode        bool
);


create table jar_entry_content (
--  cs_id              tbd foreign key references jar_code_source(id),
    content            bytea
);

