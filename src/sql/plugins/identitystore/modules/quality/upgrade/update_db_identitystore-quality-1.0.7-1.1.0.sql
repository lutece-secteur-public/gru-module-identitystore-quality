alter table identitystore_quality_suspicious_identity add column duplicate_customer_id varchar(50);
create table identitystore_suspicion_action (
    id_suspicion_action int AUTO_INCREMENT,
    customer_id         varchar(50) default '' NOT NULL,
    action_type         varchar(50) NOT NULL,
    date                timestamp(3)   NOT NULL,
    PRIMARY KEY (id_suspicion_action)
);