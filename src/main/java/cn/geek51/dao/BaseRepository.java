package cn.geek51.dao;

import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.lang.reflect.ParameterizedType;
import java.util.List;

/**
 * 持久层基类（MyBatis）
 */
@Repository
public class BaseRepository<T> {

    @Autowired
    protected SqlSessionTemplate sqlSessionTemplate;

    public String getClassName() {
        Class<T> tClass = (Class<T>) ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[0];
        return tClass.getName();
    }

    public int insertSelective(Object param, String nameSpace) {
        String methodName = Thread.currentThread().getStackTrace()[1].getMethodName();
        return sqlSessionTemplate.insert(nameSpace + methodName, param);
    }

    public int deleteOneByParams(Object param, String nameSpace) {
        String methodName = Thread.currentThread().getStackTrace()[1].getMethodName();
        return sqlSessionTemplate.delete(nameSpace + methodName, param);
    }

    public T selectOneByPrimaryKey(Object id, String nameSpace) {
        String methodName = Thread.currentThread().getStackTrace()[1].getMethodName();
        return sqlSessionTemplate.selectOne(nameSpace + methodName, id);
    }

    public List<T> selectAllByParams(Object param, String namespace) {
        String methodName = Thread.currentThread().getStackTrace()[1].getMethodName();
        return sqlSessionTemplate.selectList(namespace + methodName, param);
    }

    public int updateByPrimaryKeySelective(Object param, String nameSpace) {
        String methodName = Thread.currentThread().getStackTrace()[1].getMethodName();
        return sqlSessionTemplate.update(nameSpace + methodName, param);
    }

    public int getCount(String nameSpace) {
        String methodName = Thread.currentThread().getStackTrace()[1].getMethodName();
        return sqlSessionTemplate.selectOne(nameSpace + methodName);
    }
}
