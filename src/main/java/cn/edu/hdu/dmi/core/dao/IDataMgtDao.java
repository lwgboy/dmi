
package cn.edu.hdu.dmi.core.dao;

import java.sql.SQLException;
import java.util.List;

import cn.edu.hdu.dmi.core.model.DataImportPromptMessage;





public interface IDataMgtDao {

	public String saveOrUpdateDatas(List<Object> dataList, String type,DataImportPromptMessage promtMsg,String operateType,int gid);
	
	public List<Object> queryObjects(String type,String condition) ;
	
	public String deleteData(String type, int gid) throws SQLException;
	
	public int getCount(String type, String condition);
}
