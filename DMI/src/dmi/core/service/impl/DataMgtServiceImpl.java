package dmi.core.service.impl;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.hssf.usermodel.HSSFDateUtil;
import org.apache.poi.hssf.usermodel.HSSFFont;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;

import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;

import dmi.core.config.DateMGConfigurator;
import dmi.core.config.constants.ConfigConstants;
import dmi.core.config.model.DMField;
import dmi.core.config.model.DMTable;
import dmi.core.dao.IDataMgtDao;
import dmi.core.dao.impl.DataMgtDaoImpl;
import dmi.core.model.CellInfo;
import dmi.core.model.DataImportPromptMessage;
import dmi.core.model.Dictionary;
import dmi.core.model.RstObject;
import dmi.core.service.DictionaryService;
import dmi.core.service.IDataMgtService;
import dmi.utils.Logger;



public class DataMgtServiceImpl implements IDataMgtService{
	private static final Logger LOGGER = Logger.getLogger(DataMgtServiceImpl.class);
	private IDataMgtDao dataMgtDao = new DataMgtDaoImpl();
	
	private DictionaryService dictionaryService = new DictionaryService();
	private Gson gson = new Gson();
	@Override
	public String importData(String typeId, File file, DataImportPromptMessage promtMsg) {

		/* check the import template */
		this.checkImportExcelTemplate(typeId,file,promtMsg);
		
        List<Object> objs = this.getDataFromExcel(file, 1, typeId, promtMsg);
  
        if(promtMsg.getErrorNum() <= 0){
        	dataMgtDao.saveOrUpdateDatas(objs, typeId, promtMsg,"save",-1);
        }    	
		return null;
	}

	public String saveOrUpdateSingleData(HttpServletRequest request, String tableId, DataImportPromptMessage promtMsg, String operateType, int gid){
		List<Object> objs = new ArrayList<Object>();
		DMTable dmTable= DateMGConfigurator.getDMTableByType(tableId);
		
		Object obj = null;
    	try{
	    	Class<?> cl= Class.forName(dmTable.getClasspath());
	    	obj= cl.newInstance();
	    	
	    	for(int i = 0,paramsIndex=0; i < dmTable.getFields().size(); i++,paramsIndex++){
	    		DMField dmField = dmTable.getFields().get(i);
	    		Object[] objArrs = null;
	    		Class<?>[] classObjArrs = null;
				if( ConfigConstants.getTrue().equals(dmField.getNotEditFlag()) &&
				   !ConfigConstants.getTrue().equals(dmField.getIsPrimaryKey()) ){
					paramsIndex--;
					continue;
				}
	    		
	 
			    String reqStr = request.getParameter(dmField.getName());
			    if("".equals(reqStr)&&"yes".equals(dmField.getIsNull())){
			    	continue;
			    }
			    
			    
	    		 try{

		    		switch (dmField.getType()){
			    		case "String":
			    			objArrs = new Object[]{reqStr};
			    			classObjArrs = new Class[]{String.class};
			    			break;
			    		case "double":
			    			objArrs = new Object[]{Double.parseDouble(reqStr)};
			    			classObjArrs = new Class[]{Double.TYPE};  //基本类型用该方式
			    			break;
			    		case "int":
			    			objArrs = new Object[]{Integer.parseInt(reqStr)};
			    			classObjArrs = new Class[]{Integer.TYPE};  
			    			break;
	    	    		case "Integer":
	    	    			objArrs = new Object[]{Integer.parseInt(reqStr)};
	    	    			classObjArrs = new Class[]{Integer.class}; 
	    	    			break;
			    		case "float":
			    			objArrs = new Object[]{Float.parseFloat(reqStr)};
			    			classObjArrs = new Class[]{Float.TYPE};  
			    			break;
			    		default:
			    			objArrs = new Object[]{reqStr};	
		    		}
		        }catch(Exception e){
		
		        	
		        	
		        }
	    		cl.getDeclaredMethod(DateMGConfigurator.getSetMethodByFieldStr(dmField.getName()), classObjArrs).invoke(obj, objArrs);
	    	}
    	
    	
    	}catch(Exception e){
    		
    		
    		
    	}
		
		
		objs.add(obj);

		dataMgtDao.saveOrUpdateDatas(objs, tableId, promtMsg,operateType,gid);
		
		RstObject rstObject = new RstObject();
		rstObject.setExeSuccess(true);
		return gson.toJson(rstObject);
		
	}
	/**
	 * 
	 * delete data
	 * 
	 * @see dmi.core.service.IDataMgtService#deleteData(java.lang.String, int)
	 * @param tableId //table id
	 * @param gid     //delete data id
	 * @return
	 */
	public String deleteData(String tableId, int gid){
		try {
			dataMgtDao.deleteData(tableId, gid);
		} catch ( SQLException e) {
			e.printStackTrace();
			LOGGER.error("delete data error!");
		}
		return gson.toJson("success");
	}

	@SuppressWarnings("unused")
	private List<Object> getDataFromExcel(File file, int ignoreRows,String type, DataImportPromptMessage promtMsg) {
		List<Object> objs = new ArrayList<Object>();
		List<String> params = new ArrayList<String>();
		DMTable dmTable= DateMGConfigurator.getDMTableByType(type);
		int ColumnNum = dmTable.getFields().size();
		try {
			BufferedInputStream in = new BufferedInputStream(new FileInputStream(file));
			// 打开HSSFWorkbook
			POIFSFileSystem fs = new POIFSFileSystem(in);
			HSSFWorkbook wb = new HSSFWorkbook(fs);
			HSSFCell cell = null;
			//取得sheet的数目
			int  sheetNum = wb.getNumberOfSheets();
			for (int sheetIndex = 0; sheetIndex < sheetNum; sheetIndex++) {
				
				//获取sheet对象
				HSSFSheet sheet = wb.getSheetAt(sheetIndex);
				//取得行的数目
				int rowNum = sheet.getLastRowNum();
				for (int rowIndex = ignoreRows; rowIndex <= rowNum; rowIndex++) {
					//获取行对象
					HSSFRow row = sheet.getRow(rowIndex);
					//跳过空白行
					if (row == null) {
						continue;
					}
		
					//取得列的数目
					//int ColumnNum = row.getLastCellNum();
					params.clear();
					for (int columnIndex = 0; columnIndex <= ColumnNum; columnIndex++) {
						String value = "";
						cell = row.getCell(columnIndex);
						if (cell != null) {
							// 注意：一定要设成这个，否则可能会出现乱码
							// cell.setEncoding(HSSFCell.ENCODING_UTF_16);
							switch (cell.getCellType()) {
								case HSSFCell.CELL_TYPE_STRING:
									value = cell.getStringCellValue();
									break;
								case HSSFCell.CELL_TYPE_NUMERIC:
									if (HSSFDateUtil.isCellDateFormatted(cell)) {
										Date date = cell.getDateCellValue();
										if (date != null) {
											value = new SimpleDateFormat(
													"yyyy-MM-dd").format(date);
										} else {
											value = "";
										}
									} else {
										value = subZeroAndDot(cell.getNumericCellValue()+"");//1.0->1   1.010->1.01
									}
									break;
								case HSSFCell.CELL_TYPE_FORMULA:
									// 导入时如果为公式生成的数据则无值
									if (!cell.getStringCellValue().equals("")) {
										value = cell.getStringCellValue();
									} else {
										value = cell.getNumericCellValue() + "";
									}
									break;
								case HSSFCell.CELL_TYPE_BLANK:
									value = "";
									break;
								case HSSFCell.CELL_TYPE_ERROR:
									value = "";
									break;
								case HSSFCell.CELL_TYPE_BOOLEAN:
									value = (cell.getBooleanCellValue() == true ? "Y": "N");
									break;
								default:
									value = "";
							}
						}
						params.add(value.trim());

					}

					
					Object obj = this.getObjInstance(type,params,rowIndex+1, promtMsg);
					objs.add(obj);
					
					
				}
				
				//默认只处理第一个sheet。其余跳过
				break;
			}
			in.close();
		} catch (Exception e) {
			promtMsg.addSysExceptionMsgList(e.getMessage());
		}

		return objs;
	}

    
	private Object getObjInstance(String type,List<String> params, int row, DataImportPromptMessage promtMsg){
    	DMTable dmTable= DateMGConfigurator.getDMTableByType(type);
    	Object obj = null;
    	double x=0,y=0;

    	try{
    		
	    	Class<?> cl= Class.forName(dmTable.getClasspath());
	    	obj= cl.newInstance();
	    	
	    	for(int i = 0,paramsIndex=0; i < dmTable.getFields().size(); i++,paramsIndex++){
	    		DMField dmField = dmTable.getFields().get(i);
	    		Object[] objArrs = null;
	    		Class<?>[] classObjArrs = null;
				if("true".equals(dmField.getNotEditFlag())){
					paramsIndex--;
					continue;
				}
	    		String value=params.get(paramsIndex);
				
	    		/* 一些输入校验 */
	    		/* 必填项 */
	    		if("no".equals(dmField.getIsNull())&&(null == value || "".equals(value))){
	    			promtMsg.addErrorToList(new CellInfo(row, paramsIndex+1, "不能为空"));
	    			continue;
	    		}
	    		if(dmField.getMaxLength()!=null&&Integer.parseInt(dmField.getMaxLength())<value.length()){
	    			promtMsg.addErrorToList(new CellInfo(row, paramsIndex+1, "超过最大长度"));
	    			continue;
	    		}
	    		
	    		// TODO 对导入图片表的module字段做一个特殊处理,待处理

				/* do some data convert */
				if("singleSelect".equals(dmField.getInputType())&&!"".equals(value)){
	        		if("inner".equals(dmField.getValueFrom())){
	        			LinkedTreeMap<Object,Object> linkedMap =  gson.fromJson(dmField.getValueSource(), LinkedTreeMap.class);
	        			Set<Object> set = linkedMap.keySet();
	        			boolean isFind=false;
	        			String warnMsg="/";
						for(Object s:set){
							warnMsg+=(String) linkedMap.get(s)+"/";
							if(value.indexOf((String) linkedMap.get(s))!=-1){
								value=(String) s;
								isFind=true;
								break;
							}
						}
						if(isFind==false){
							promtMsg.addErrorToList(new CellInfo(row, paramsIndex+1,"只能输入包含以下字符串"+warnMsg+"中的一个"));
						}
	        		}
	        	}
				/*
				if("multipleSelect".equals(dmField.getInputType())&&!"".equals(value)){
					if ("ajax".equals(dmField.getValueFrom())) {
						
						List<Dictionary> dictionaryList = dictionaryService.getDictionaryListByType(dmField.getValueParam());
						int ms = 0;
						String warnMsg="/";
						for (Dictionary d : dictionaryList) {
							warnMsg+=d.getDescription()+"/";
							if ((value.indexOf(d.getDescription()) != -1)) {
								ms = ms | Integer.parseInt(d.getValue());
							}
						}
						if(ms==0&&"no".equals(dmField.getIsNull())) {//必填项，未输入正确的值
							promtMsg.addErrorToList(new CellInfo(row, paramsIndex+1, "只能输入包含以下字符串"+warnMsg));
						}
						value = ms + "";
					}
				}
				*/
	    		
	    		 try{

		    		switch (dmField.getType()){
			    		case "String":
			    			objArrs = new Object[]{value};
			    			classObjArrs = new Class[]{String.class};
			    			break;
			    		case "double":
			    			if("".equals(value.trim())&&"yes".equals(dmField.getIsNull())){
			    				objArrs = new Object[]{0};
							}else{
								objArrs = new Object[]{Double.parseDouble(value)};
							}
			    			
			    			classObjArrs = new Class[]{Double.TYPE};  //基本类型用该方式
			    			break;
			    		case "int":
			    			if("".equals(value.trim())&&"yes".equals(dmField.getIsNull())){
			    				objArrs = new Object[]{0};
			    			}else{
			    				objArrs = new Object[]{Integer.parseInt(value)};
			    			}
			    			
			    			classObjArrs = new Class[]{Integer.TYPE};  
			    			break;
	    	    		case "Integer":
			    			if("".equals(value.trim())&&"yes".equals(dmField.getIsNull())){
			    				objArrs = new Object[]{null};
			    			}else{
			    				objArrs = new Object[]{Integer.parseInt(value)};
			    			}
	    	    			classObjArrs = new Class[]{Integer.class}; 
	    	    			break;
			    		case "float":
			    			if("".equals(value.trim())&&"yes".equals(dmField.getIsNull())){
			    				objArrs = new Object[]{Float.parseFloat(value)};
			    			}else{
			    				objArrs = new Object[]{Float.parseFloat(value)};
			    			}
			    			
			    			classObjArrs = new Class[]{Float.TYPE};  
			    			break;
			    		default:
			    			objArrs = new Object[]{value};	
		    		}
		        }catch(Exception e){
		        	promtMsg.addErrorToList(new CellInfo(row, paramsIndex+1, "数据异常"));
		        	continue;
		        }
	    		cl.getDeclaredMethod(DateMGConfigurator.getSetMethodByFieldStr(dmField.getName()), classObjArrs).invoke(obj, objArrs);
	    	}
    	
    	
    	}catch(Exception e){
    		promtMsg.addSysExceptionMsgList(e.getMessage());
    	}

    	
    	return obj;

    }


	public String queryDataByName(String tableId, String keyWord,int start,int size){
		
		List<Object> list = dataMgtDao.queryObjects(tableId, "where obj.name like '%"+keyWord+"%'"+" limit  "+ start+","+size);
		
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("list", list);
        map.put("totalNum", this.getCountByName(tableId, keyWord));

		return gson.toJson(map);
	}
	
	

	/**
	 * 〈一句话功能简述〉
	 * 〈功能详细描述〉
	 * @see com.cetiti.omot.datamaint.service.IDataMgtService#exportData(java.lang.String)
	 * @param type
	 * @return
	 */
	@Override
	public InputStream exportData(String type) {
		InputStream is = null;
		List<Object> list = dataMgtDao.queryObjects(type,"");
		
       
        DMTable dmTable= DateMGConfigurator.getDMTableByType(type);

		Workbook workbook = new HSSFWorkbook();
		Sheet sheet = workbook.createSheet("Sheet1");
		Row row = sheet.createRow(0);
		
		/**/
		CellStyle style = workbook.createCellStyle();
		HSSFFont font = (HSSFFont) workbook.createFont();
		font.setBoldweight(HSSFFont.BOLDWEIGHT_BOLD);//粗体显示
		style.setFont(font);
		
		for(int i = 0; i < dmTable.getFields().size();i++){
			DMField field = dmTable.getFields().get(i);
			Cell cell = row.createCell(i);
			cell.setCellValue(field.getTitle());
			cell.setCellStyle(style);
			//if(field.getName().equals("name")) name = field.getTitle();
		}
		try {
			Class<?> cl= Class.forName(dmTable.getClasspath());
			String geometry = "";
			for(int i = 0; i < list.size(); i++){
				row = sheet.createRow(i+1);
				Object obj = list.get(i);

		
				for(int j = 0; j < dmTable.getFields().size();j++){
					DMField field = dmTable.getFields().get(j);

					String value="";
					Object objFiled = cl.getDeclaredMethod(DateMGConfigurator.getGetMethodByFieldStr(field.getName()), new Class[]{}).invoke(obj, new Object[]{});
					if(objFiled==null) {
						value="";
					}else{
						value = objFiled.toString();
					}
					 
					
					if(value==null){
						value="";
					}
					
					/* do some data convert */
					if("singleSelect".equals(field.getInputType())){
		        		if("inner".equals(field.getValueFrom())){
		        			LinkedTreeMap<Object,Object> linkedMap =  gson.fromJson(field.getValueSource(), LinkedTreeMap.class);
		        			Set<Object> set = linkedMap.keySet();
							for(Object s:set){
								if(value.equals(s)){
									value=(String) linkedMap.get(s);
								}
							}
		        		}
		        	}
					if("multipleSelect".equals(field.getInputType())&&!"".equals(value)){
     		    	   if("ajax".equals(field.getValueFrom())){
     		    		   int iValue=Integer.parseInt(value);
     		    		  List<Dictionary> dictionaryList = dictionaryService.getDictionaryListByType(field.getValueParam());
     		    		  String checkStr="";
     		    		  for(Dictionary d:dictionaryList){
     		    			   if((iValue&Integer.parseInt(d.getValue()))==Integer.parseInt(d.getValue())){
        	   	    			   if(d.getDescription().equals("全部")){
	        	    				   checkStr=(""+d.getDescription()+", ");
	        	    				   break;
	        	    			   }
        	    				   checkStr+=(""+d.getDescription()+", ");
        	    			   } 
     		    		  }
     		    		  value=checkStr;
     		    	   }
					}
					
					
					
					row.createCell(j).setCellValue(value);
					//if(field.getName().equals("name")) name = field.getTitle();
				}
			}
		
		
		
		
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
		
		
			workbook.write(baos);
			byte[] aa = baos.toByteArray();
			is = new ByteArrayInputStream(aa,0,aa.length);
			baos.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return is;
	}

	/**
	 * 〈一句话功能简述〉
	 * 〈功能详细描述〉
	 * @see com.cetiti.omot.datamaint.service.IDataMgtService#downloadTemplate(java.lang.String)
	 * @param type
	 * @return
	 */
	@Override
	public InputStream downloadTemplate(String type) {
		InputStream is = null;
        DMTable dmTable= DateMGConfigurator.getDMTableByType(type);

		Workbook workbook = new HSSFWorkbook();
		Sheet sheet = workbook.createSheet("Sheet1");
		Row row = sheet.createRow(0);
		/**/
		CellStyle style = workbook.createCellStyle();
		HSSFFont font = (HSSFFont) workbook.createFont();
		font.setBoldweight(HSSFFont.BOLDWEIGHT_BOLD);//粗体显示
		style.setFont(font);

		for(int i = 0,  cellIndex=0; i < dmTable.getFields().size();i++,cellIndex++){
			DMField field = dmTable.getFields().get(i);
			
			if("true".equals(field.getNotEditFlag())) {
				
				cellIndex--;
				continue;
			}
			Cell cell = row.createCell(cellIndex);
			cell.setCellValue(field.getTitle());
			cell.setCellStyle(style);
			//if(field.getName().equals("name")) name = field.getTitle();
		}

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try {
			
			workbook.write(baos);
			byte[] aa = baos.toByteArray();
			is = new ByteArrayInputStream(aa,0,aa.length); 

			baos.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return is;
	}
	
	private void checkImportExcelTemplate(String type, File file, DataImportPromptMessage promtMsg){
		boolean isError = false;
		try {
			BufferedInputStream in = new BufferedInputStream(new FileInputStream(file));
			// 打开HSSFWorkbook
			POIFSFileSystem fs = new POIFSFileSystem(in);
			HSSFWorkbook wb = new HSSFWorkbook(fs);
			HSSFCell cell = null;
			//取得sheet的数目
			int  sheetNum = wb.getNumberOfSheets();
			for(int sheetIndex = 0; sheetIndex < sheetNum; sheetIndex++) {
				
				//获取sheet对象
				HSSFSheet sheet = wb.getSheetAt(sheetIndex);
				//取得行的数目
				int rowNum = sheet.getLastRowNum();
				if(rowNum < 0) isError = true;
		
					//获取行对象
					HSSFRow row = sheet.getRow(0);
					if (row == null) isError = true;
				
		
					//取得列的数目
					int ColumnNum = row.getLastCellNum();
		
					for (int columnIndex = 0,cellIndex = 0; columnIndex < ColumnNum; columnIndex++,cellIndex++) {
						DMTable dmTable= DateMGConfigurator.getDMTableByType(type);
						if("true".equals(dmTable.getFields().get(columnIndex).getNotEditFlag())){
							cellIndex--;
							continue;
						}
						String value = "";
						cell = row.getCell(cellIndex);
						if (cell != null) {
							switch (cell.getCellType()) {
								case HSSFCell.CELL_TYPE_STRING:
									value = cell.getStringCellValue();
									break;
								case HSSFCell.CELL_TYPE_NUMERIC:
									if (HSSFDateUtil.isCellDateFormatted(cell)) {
										Date date = cell.getDateCellValue();
										if (date != null) {
											value = new SimpleDateFormat(
													"yyyy-MM-dd").format(date);
										} else {
											value = "";
										}
									} else {
										value = cell.getNumericCellValue()+"";
									}
									break;
								case HSSFCell.CELL_TYPE_FORMULA:
									// 导入时如果为公式生成的数据则无值
									if (!cell.getStringCellValue().equals("")) {
										value = cell.getStringCellValue();
									} else {
										value = cell.getNumericCellValue() + "";
									}
									break;
								case HSSFCell.CELL_TYPE_BLANK:
									break;
								case HSSFCell.CELL_TYPE_ERROR:
									value = "";
									break;
								case HSSFCell.CELL_TYPE_BOOLEAN:
									value = (cell.getBooleanCellValue() == true ? "Y"
											: "N");
									break;
								default:
									value = "";
							}
							/* check if the title is right */
							
							 if(!dmTable.getFields().get(columnIndex).getTitle().equals(value)){
								 isError = true;
								 CellInfo cellError = new CellInfo(1,cellIndex+1,"模板错误");
								 promtMsg.addErrorToList(cellError);
							 }
						}else{
							isError = true;
						}
						
						
					}


			
				
				//默认只处理第一个sheet。其余跳过
				break;
			}
			in.close();
		} catch (Exception e) {
			promtMsg.addSysExceptionMsgList(e.getMessage());
		}

		if(isError==true){
			promtMsg.setTemplateError("模板错误");
		}
	}
	private  String subZeroAndDot(String s){  
        if(s.indexOf(".") > 0){  
            s = s.replaceAll("0+?$", "");//去掉多余的0  
            s = s.replaceAll("[.]$", "");//如最后一位是.则去掉  
        }  
        return s;  
    }



	/**
	 * 〈一句话功能简述〉
	 * 〈功能详细描述〉
	 * @see com.cetiti.omot.datamaint.service.IDataMgtService#querySingleDataByGid(int)
	 * @param gid
	 * @return
	 */
	@Override
	public Object querySingleDataByGid(String type,int gid) {
		List<Object> list = dataMgtDao.queryObjects(type,"where obj.gid = '"+gid+"'");
		if(list.size()>0){
			return list.get(0);
		}else{
			return null;
		}
		
	}

	/**
	 * 〈一句话功能简述〉
	 * 〈功能详细描述〉
	 * @see com.cetiti.omot.datamaint.service.IDataMgtService#getCount(java.lang.String, java.lang.String)
	 * @param type
	 * @param name
	 * @return
	 */
	@Override
	public int getCountByName(String type, String name) {
		// TODO Auto-generated method stub
		return dataMgtDao.getCount(type,"where obj.name like '%"+name+"%'");
	}  

}
