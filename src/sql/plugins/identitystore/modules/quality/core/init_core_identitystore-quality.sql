
--
-- Data for table core_admin_right
--
DELETE FROM core_admin_right WHERE id_right = 'IDENTITYSTORE_QUALITY_MANAGEMENT';
INSERT INTO core_admin_right (id_right,name,level_right,admin_url,description,is_updatable,plugin_name,id_feature_group,icon_url,documentation_url, id_order ) VALUES 
('IDENTITYSTORE_QUALITY_MANAGEMENT','module.identitystore.quality.adminFeature.ManageQuality.name',1,'jsp/admin/plugins/identitystore/modules/quality/ManageSuspiciousIdentitys.jsp','identitystore-quality.adminFeature.ManageQuality.description',0,'identitystore-quality',NULL,NULL,NULL,4);


--
-- Data for table core_user_right
--
DELETE FROM core_user_right WHERE id_right = 'IDENTITYSTORE_QUALITY_MANAGEMENT';
INSERT INTO core_user_right (id_right,id_user) VALUES ('IDENTITYSTORE_QUALITY_MANAGEMENT',1);

