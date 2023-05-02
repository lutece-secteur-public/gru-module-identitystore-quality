
--
-- Structure for table identitystore_quality_suspicious_identity
--

DROP TABLE IF EXISTS identitystore_quality_suspicious_identity_excluded;
CREATE TABLE identitystore_quality_suspicious_identity_excluded (
id_suspicious_identity_excluded int AUTO_INCREMENT,
id_suspicious_identity_master int NOT NULL,
id_suspicious_identity_child int NOT NULL,
id_duplicate_rule int NOT NULL,
PRIMARY KEY (id_suspicious_identity_excluded)
);
alter table identitystore_quality_suspicious_identity_excluded
    add constraint identitystore_quality_suspicious_identity_excluded_pk
        primary key (id_suspicious_identity_excluded);

