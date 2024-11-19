package ru.flamexander.db.interaction.ex05_repositories;

import ru.flamexander.db.interaction.exceptions.ApplicationException;
import ru.flamexander.db.interaction.exceptions.ApplicationInitializationException;
import ru.flamexander.db.interaction.exceptions.EntityException;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.stream.Collectors;

public class AbstractRepository<T> {
    private static final String GETTER_PREFIX = "get";
    private static final String SETTER_PREFIX = "set";
    private static final String DEFAULT_ID_FIELD = "id";

    private DataSource dataSource;

    private PreparedStatement psCreate;

    private PreparedStatement psRead;

    private PreparedStatement psDelete;

    private PreparedStatement psUpdate;

    private List<EntityFieldWrapper> cachedFields;

    private EntityFieldWrapper cachedIdField;

    private Class<T> clazz;

    public AbstractRepository(DataSource dataSource, Class<T> cls) {
        this.dataSource = dataSource;
        this.clazz = cls;
        init();
    }

    private void init() {
        cachedFields = getEntityFields(clazz);
        cachedIdField = getEntityIdField(clazz);
        prepareStatementCreate(clazz);
        prepareStatementRead(clazz);
        prepareStatementUpdate(clazz);
        prepareStatementDelete(clazz);
        System.out.println("Инициализирован репозиторий для сущности " + clazz.getSimpleName());

    }

    public void disconnect(){
        try {
            if (psCreate != null) psCreate.close();
            if (psRead != null) psRead.close();
            if (psDelete != null) psDelete.close();
            if (psUpdate != null) psUpdate.close();
        } catch (SQLException e) {
            throw new ApplicationException("Не удалось закрыть репозиторий");
        }
    }

    private void prepareStatementCreate(Class<T> cls) {
        StringBuilder query = new StringBuilder("insert into ");
        String tableName = cls.getAnnotation(RepositoryTable.class).title();
        query.append(tableName).append(" (");
        // 'insert into users ('
        for (EntityFieldWrapper f : cachedFields) {
            query.append(f.getRemoteField()).append(", ");
        }
        // 'insert into users (login, password, nickname, '
        query.setLength(query.length() - 2);
        // 'insert into users (login, password, nickname'
        query.append(") values (");
        for (EntityFieldWrapper f : cachedFields) {
            query.append("?, ");
        }
        // 'insert into users (login, password, nickname) values (?, ?, ?, '
        query.setLength(query.length() - 2);
        // 'insert into users (login, password, nickname) values (?, ?, ?'
        query.append(");");
        try {
            psCreate = dataSource.getConnection().prepareStatement(query.toString(), Statement.RETURN_GENERATED_KEYS);
        } catch (SQLException e) {
            e.printStackTrace();
            throw new ApplicationInitializationException("Ошибка подготовки запроса");
        }
    }

    private void prepareStatementRead(Class<T> cls) {
        String tableName = cls.getAnnotation(RepositoryTable.class).title();
        StringBuffer builder = new StringBuffer("select * from ");
        builder.append(tableName);
        builder.append(" where ");
        builder.append(cachedIdField.getRemoteField());
        builder.append(" = ?;");
        try {
            psRead = dataSource.getConnection().prepareStatement(builder.toString());
        } catch (SQLException e) {
            e.printStackTrace();
            throw new ApplicationInitializationException("Ошибка подготовки запроса");
        }
    }

    private void prepareStatementUpdate(Class<T> cls) {
        StringBuilder query = new StringBuilder("update ");
        String tableName = cls.getAnnotation(RepositoryTable.class).title();
        query.append(tableName).append(" set ");
        // 'update users set'
        for (EntityFieldWrapper f : cachedFields) {
            query.append(f.getRemoteField()).append(" = ?, ");
        }
        // 'update users set login =?, password = ?, nickname = ?, '
        query.setLength(query.length() - 2);
        // 'update users set login =?, password = ?, nickname = ?'
        query.append(" where ")
                .append(cachedIdField.getRemoteField())
                .append(" = ?;");
        // 'update users set login =?, password = ?, nickname = ? where id = ?;'
        try {
            psUpdate = dataSource.getConnection().prepareStatement(query.toString());
        } catch (SQLException e) {
            e.printStackTrace();
            throw new ApplicationInitializationException("Ошибка подготовки запроса");
        }
    }

    private void prepareStatementDelete(Class<T> cls) {
        String tableName = cls.getAnnotation(RepositoryTable.class).title();
        StringBuffer builder = new StringBuffer("delete from ");
        builder.append(tableName);
        builder.append(" where ");
        builder.append(cachedIdField.getRemoteField());
        builder.append(" = ?;");
        try {
            psDelete = dataSource.getConnection().prepareStatement(builder.toString());
        } catch (SQLException e) {
            e.printStackTrace();
            throw new ApplicationInitializationException("Ошибка подготовки запроса");
        }
    }

    public T create(T entity) {
        try {
            for (int i = 0; i < cachedFields.size(); i++) {
                EntityFieldWrapper wrapper = cachedFields.get(i);
                psCreate.setObject(i + 1, getFieldValue(entity, wrapper.getGetter(), wrapper.getLocalField().getType()));
            }
            psCreate.executeUpdate();
            ResultSet rs = psCreate.getGeneratedKeys();
            if (rs.next()) {
                setFieldValue(entity, cachedIdField.getSetter(), rs.getObject(cachedIdField.getRemoteField()));
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new EntityException("Ошибка сохранения объекта " + clazz.getSimpleName());
        }
        return entity;
    }

    public boolean update(T entity) {
        long id = (long) Optional.ofNullable(getFieldValue(entity, cachedIdField.getGetter(), cachedIdField.getLocalField().getType()))
                .orElseThrow(() -> new EntityException("Объект " + entity.getClass().getSimpleName() + " не сохранен в базе"));
        try {
            for (int i = 0; i < cachedFields.size(); i++) {
                EntityFieldWrapper wrapper = cachedFields.get(i);
                psUpdate.setObject(i + 1, getFieldValue(entity, wrapper.getGetter(), wrapper.getLocalField().getType()));
            }
            psUpdate.setLong(cachedFields.size() + 1, id);
            int result = psUpdate.executeUpdate();
            return result == 1;
        } catch (Exception e) {
            e.printStackTrace();
            throw new EntityException("Ошибка сохранения объекта " + clazz.getSimpleName());
        }
    }

    public List<T> findAll() {
        List<T> result = new ArrayList<>();
        String tableName = clazz.getAnnotation(RepositoryTable.class).title();
        StringBuffer builder = new StringBuffer("select * from ");
        builder.append(tableName);
        try (Statement statement = dataSource.getConnection().createStatement();
             ResultSet rs = statement.executeQuery(builder.toString())) {
            while (rs.next()) {
                try {
                    T object = clazz.getConstructor().newInstance();
                    fillEntity(rs, object);
                    result.add(object);
                } catch (Exception e) {
                    e.printStackTrace();
                    throw new EntityException("Ошибка создания объекта " + clazz.getSimpleName());
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            throw new EntityException("Ошибка поиска объекта " + clazz.getSimpleName());
        }
        return result;
    }

    public boolean deleteById(long id) {
        try {
            psDelete.setLong(1, id);
            int result = psDelete.executeUpdate();
            return result == 1;
        } catch (SQLException e) {
            e.printStackTrace();
            throw new EntityException("Ошибка удаления объекта " + clazz.getSimpleName() + ", id=" + id);
        }
    }

    public Optional<T> findById(long id) {
        T object = null;
        try {
            psRead.setLong(1, id);
            try (ResultSet rs = psRead.executeQuery()) {
                while (rs.next()) {
                    try {
                        object = clazz.getConstructor().newInstance();
                        fillEntity(rs, object);
                    } catch (Exception e) {
                        e.printStackTrace();
                        throw new EntityException("Ошибка создания объекта " + clazz.getSimpleName());
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            throw new EntityException("Ошибка поиска объекта " + clazz.getSimpleName());
        }
        return Optional.ofNullable(object);
    }

    private void fillEntity(ResultSet rs, T object) throws SQLException {
        for (EntityFieldWrapper wrapper : cachedFields) {
            Object value = rs.getObject(wrapper.getRemoteField());
            setFieldValue(object, wrapper.getSetter(), value);
        }
        Object value = rs.getLong(cachedIdField.getRemoteField());
        setFieldValue(object, cachedIdField.getSetter(), value);
    }

    private EntityFieldWrapper getEntityIdField(Class<?> cls) {
        Field field = Arrays.stream(cls.getDeclaredFields())
                .filter(f -> (f.isAnnotationPresent(RepositoryIdField.class)
                        || (f.isAnnotationPresent(RepositoryField.class) && f.getDeclaredAnnotation(RepositoryField.class).name().equalsIgnoreCase(DEFAULT_ID_FIELD))
                        || (f.isAnnotationPresent(RepositoryField.class) && f.getName().equalsIgnoreCase(DEFAULT_ID_FIELD))))
//                .peek(f -> f.setAccessible(true))
                .findFirst()
                .orElseThrow(() -> new EntityException("Сущность " + cls.getSimpleName() + " не содержит поле id c аннотацией @RepositoryIdField или @RepositoryField"));
        EntityFieldWrapper wrapper = new EntityFieldWrapper(field);
        wrapper.setGetter(getFieldMethod(field, GETTER_PREFIX));
        wrapper.setSetter(getFieldMethod(field, SETTER_PREFIX));

        String[] remoteIdField = new String[]{""};

        Optional.ofNullable(field.getDeclaredAnnotation(RepositoryIdField.class))
                .ifPresentOrElse(a -> remoteIdField[0] = a.name(), () -> remoteIdField[0] = DEFAULT_ID_FIELD);
        wrapper.setRemoteField(remoteIdField[0]);
        return wrapper;
    }

    private Method getFieldMethod(Field field, String prefix) {
        StringBuilder builder = new StringBuilder(prefix);
        builder.append(field.getName().substring(0, 1).toUpperCase());
        builder.append(field.getName().substring(1));
        String methodName = builder.toString();
        return Arrays.stream(clazz.getDeclaredMethods())
                .filter(m -> m.getName().startsWith(methodName))
                .findFirst()
                .orElseThrow(() -> new EntityException("Отсутствует метод " + methodName + " в классе " + clazz.getSimpleName()));
    }

    private List<EntityFieldWrapper> getEntityFields(Class<?> cls) {
        return Arrays.stream(cls.getDeclaredFields())
                .filter(f -> f.isAnnotationPresent(RepositoryField.class))
                .filter(f -> !f.isAnnotationPresent(RepositoryIdField.class))
//                .peek(f -> f.setAccessible(true))
                .map(f -> {
                    EntityFieldWrapper wrapper = new EntityFieldWrapper(f);
                    String annotationName = f.getAnnotation(RepositoryField.class).name();
                    wrapper.setRemoteField(annotationName.isEmpty() ? f.getName() : annotationName);
                    wrapper.setGetter(getFieldMethod(f, GETTER_PREFIX));
                    wrapper.setSetter(getFieldMethod(f, SETTER_PREFIX));
                    return wrapper;
                }).collect(Collectors.toList());
    }

    private void setFieldValue(T entity, Method method, Object value) {
        try {
            method.invoke(entity, value);
        } catch (IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
            throw new EntityException("Не удалось вызвать метод " + method.getName() + " в классе " + clazz.getSimpleName());
        }
    }

    private <K> K getFieldValue(T entity, Method method, Class<K> target) {
        try {
            Object result = method.invoke(entity);
            return target.cast(result);
        } catch (IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
            throw new EntityException("Не удалось вызвать метод " + method.getName() + " в классе " + clazz.getSimpleName());
        }
    }

    private class EntityFieldWrapper {
        private Field localField;
        private Method getter;
        private Method setter;
        private String remoteField;

        public EntityFieldWrapper(Field localField) {
            this.localField = localField;
        }

        public Field getLocalField() {
            return localField;
        }

        public void setLocalField(Field localField) {
            this.localField = localField;
        }

        public Method getGetter() {
            return getter;
        }

        public void setGetter(Method getter) {
            this.getter = getter;
        }

        public Method getSetter() {
            return setter;
        }

        public void setSetter(Method setter) {
            this.setter = setter;
        }

        public String getRemoteField() {
            return remoteField;
        }

        public void setRemoteField(String remoteField) {
            this.remoteField = remoteField;
        }
    }
}
