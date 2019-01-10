set search_path to avatar;

create sequence avatar.hibernate_sequence
    start 1
    increment 1;

create table avatar.avatars (
       id int8 not null,
        clothes varchar(255),
        clothes_graphic varchar(255),
        color_fabric varchar(255),
        eyebrows varchar(255),
        eyes varchar(255),
        facial_hair varchar(255),
        facial_hair_color varchar(255),
        hair_color varchar(255),
        mouth_types varchar(255),
        skin_colors varchar(255),
        top varchar(255),
        top_accessory varchar(255),
        username varchar(255),
        primary key (id)
    );

create index on avatar.avatars (username);
