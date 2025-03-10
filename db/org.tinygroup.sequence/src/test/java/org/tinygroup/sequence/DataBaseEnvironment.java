package org.tinygroup.sequence;

import java.util.HashMap;
import java.util.Map;

public final class DataBaseEnvironment {
    
    private static final Map<DatabaseType, Class<?>> DRIVER_CLASS_NAME = new HashMap<DatabaseType, Class<?>>(2);
    
    private static final Map<DatabaseType, String> URL = new HashMap<DatabaseType, String>(2);
    
    private static final Map<DatabaseType, String> USERNAME = new HashMap<DatabaseType, String>(2);
    
    private static final Map<DatabaseType, String> PASSWORD = new HashMap<DatabaseType, String>(2);
    
    private final DatabaseType databaseType;
    
    public DataBaseEnvironment(final DatabaseType databaseType) {
        this.databaseType = databaseType;
        fillData();
    }
    
    private void fillData() {
        DRIVER_CLASS_NAME.put(DatabaseType.H2, org.h2.Driver.class);
        DRIVER_CLASS_NAME.put(DatabaseType.MySQL, com.mysql.jdbc.Driver.class);
        URL.put(DatabaseType.H2, "jdbc:h2:mem:%s;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false;MODE=MYSQL");
        URL.put(DatabaseType.MySQL, "jdbc:mysql://localhost:3306/%s");
        USERNAME.put(DatabaseType.H2, "sa");
        USERNAME.put(DatabaseType.MySQL, "root");
        PASSWORD.put(DatabaseType.H2, "");
        PASSWORD.put(DatabaseType.MySQL, "");
    }
    
    public String getDriverClassName() {
        return DRIVER_CLASS_NAME.get(databaseType).getName();
    }
    
    public String getURL(final String dbName) {
        return String.format(URL.get(databaseType), dbName);
    }
    
    public String getUsername() {
        return USERNAME.get(databaseType);
    }
    
    public String getPassword() {
        return PASSWORD.get(databaseType);
    }

	public DatabaseType getDatabaseType() {
		return databaseType;
	}
    
}