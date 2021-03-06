package stockDataAnalysis;

import java.io.File;
import java.io.IOException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.*;
import java.time.*;
import java.nio.file.*;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import jxl.Cell;
import jxl.Sheet;
import jxl.Workbook;
import jxl.read.biff.BiffException;

public class JapanStock implements IGetStockData {
	
	public static final String jpx_url = "https://www.jpx.co.jp";
	public static final String jpx_short_current = "/english/markets/public/short-selling/index.html";
	public static final String jpx_short_archived_base = "/english/markets/public/short-selling/";
	public String jpx_short_archived_url;

	/* (non-Javadoc)
	 * @see stockDataAnalysis.IGetStockData#getShortPositions(java.lang.String, java.lang.String, java.lang.String)
	 */
	@Override
	public ArrayList <StockItem>  getShortPositions(String date) {
		String urlBase;
		urlBase = getStockBaseUrl(date);
		ArrayList <StockItem> list;
		if (urlBase == null) {
			System.out.println("Could not get short positions");
			return null;
		}

		//Save webpage in current path
		Path currentRelativePath = Paths.get("");
		String httpFilePath = currentRelativePath.toAbsolutePath().toString();
		httpFilePath = httpFilePath + "/temp";
		String httpFileName = "temp.xml";
		String fileName = "japan.xls";
		
		if (StockDataDownload.HttpDownloadPage(urlBase, httpFilePath, httpFileName) == false) {
			System.out.println("Could not get the webpage from " + urlBase);
			return null;
		}
		
		String url;
		url = GetShortPositionUrl(httpFilePath, httpFileName, date);
		if (url != null)
			StockDataDownload.HttpDownloadFile(url, httpFilePath, fileName);
		else
			return null;
		
	
            File file = new File(httpFilePath + "/" + fileName);
 
            Workbook book;
			try {
				book = Workbook.getWorkbook(file);
				list = JapanStockRead(book);
	            book.close();
	            return list;
			} catch (BiffException | IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} 
           
		return null;
	}
	

	/**
	 * Get the time difference in month between input date to current Japan local time
	 * @param date : format should be "yyyymmdd". it should be Japan local time
	 * @return :  date difference in month, the value will be -1 if it is bigger than 12 or input date is later than
	 * current time.
	 */
	public int getDateDifference(String date) {
		//Get current Japan local time
		ZonedDateTime zdt = ZonedDateTime.now(ZoneId.of("Asia/Tokyo"));
		int currentYear = zdt.getYear();
		int currentMonth = zdt.getMonthValue();
		
		//Compare with the specified date from input argument
		int inputYear = Integer.parseInt(date.subSequence(0, 4).toString());
		int inputMonth = Integer.parseInt(date.subSequence(4, 6).toString());
		
		System.out.println("local Year " + currentYear + " local month " + currentMonth
				+ " inputYear " + inputYear + " inputMonth " + inputMonth);
		
		int currentTime = currentYear*12 + currentMonth;
		int inputTime = inputYear*12 + inputMonth;
		
		if (inputTime > currentTime) {
			System.out.println("Error, the date is later than current time");
			return -1;
		} else {
			if ((currentTime - inputTime) > 12) {
				System.out.println("Error, could not get the data of 12 month's eariler");
				return -1;
			} else {
				return currentTime - inputTime;
			}
		}
		
	}
	
	/**
	 * @param date. Format should be "yyyymmdd"
	 * @return . URL of the stock data
	 */
	public String getStockBaseUrl(String date) {
		int diff = getDateDifference(date);
		String day;
		String url = null;
		if (diff == -1)
		{
			System.out.println("Could not get the URL");
			return url;
		}
		
		if (diff == 0) {
			url = jpx_url + jpx_short_current;
		} else {
			if (diff < 10) {
				day = "0" + Integer.toString(diff-1); //temp solution
			} else {
				day = Integer.toString(diff); //temp solution
			}
			url = jpx_url + jpx_short_archived_base + "00-archives-" + day + ".html";
		}
		System.out.println("URL is: " + url);
		return url;
	}

	/**
	 * Parse the webpage download from https://www.jpx.co.jp
	 * @param filePath ： webpage's path
	 * @param fileName ： webpage's name
	 * @param date: format is "YYYYMMDD", it should be Japan local time
	 * @return
	 */
	public String GetShortPositionUrl(String filePath, String fileName, String date){
		ArrayList<String> list=null;
	
		
		//Get all links from webpage's table
		try {
			File file = new File(filePath + File.separator + fileName);
			Document doc = Jsoup.parse(file, "GBK");
			Elements elements = doc.getElementsByClass("component-normal-table");

			list = new ArrayList<String>();

			for(Element element:elements){
				if(element.text()!=null){
					Elements es = element.select("tr");
					for(Element tdelement:es){
						Elements tdes = tdelement.select("td");
						for(int i = 0; i < tdes.size(); i++){
						list.add(tdes.get(i).html());
						}
					}
				}
			}
			//For debug purpose only
	//		for (String text:list) {
	//			System.out.println(text);
	//		}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		//Get the URL based on date
		String link = null;
		for (String item:list) {
			if(item.contains(date)) {
				link = item.substring(item.indexOf('/'), item.indexOf("xls"));
			}
		}
		if (link == null) {
			System.out.println(date + " Stock market is closed");
			return link;
		}
		System.out.println("link is " + link);
		link = jpx_url + link + "xls";
		System.out.println("link is " + link);
		return link;
		
	}
	
   
    public static ArrayList <StockItem> JapanStockRead (Workbook book) {
        int rows;
        //Get sheet numbers in workbook
        int sheetNumber = book.getNumberOfSheets();
        //Get sheet name
        String [] sheetNameList = book.getSheetNames();
        //Get each sheet
        Sheet [] sheetList = book.getSheets();
        
        ArrayList <StockItem> japanStockList = new ArrayList <StockItem> ();
        
        //iterate each cell and save it in the ArrayList
        for(int i = 0;i < sheetNumber;i++) {
      //      System.out.println("############## " + sheetNameList[i] + " ##############");
            //Get row's number in each sheet
            rows = sheetList[i].getRows();
            for(int j = 0;j < rows;j++) {
                //Get each row
                Cell [] cellList = sheetList[i].getRow(j);
                StockItem stockItem = new StockItem();
                if (cellList[1].getContents().contains("/") == false) {
      //          	System.out.println("Skip this row" + cellList[0].getContents());
                	continue;
                }
                stockItem.date = cellList[1].getContents();
                stockItem.stockCode = cellList[2].getContents();
                stockItem.shortRatio = Double.parseDouble(cellList[10].getContents().replaceAll("%", ""));

                japanStockList.add(stockItem);
     /*
                System.out.println("date " + stockItem.date + "code " + stockItem.stockCode + "shortRatio " + stockItem.shortRatio);
                        
                for (Cell cell : cellList) {
                    System.out.print(cell.getContents() + "  ");
                }
                System.out.println();
     */ 
            }  
       
        }
        StockItem stockItem = new StockItem();
        stockItem.stockCode = " ";
        ArrayList <StockItem> finalList = new ArrayList <StockItem> ();
        //Combine each company's stock short ratio together
        for (StockItem item : japanStockList) {
        	if (stockItem.stockCode.contentEquals(item.stockCode)) {
        		stockItem.shortRatio = stockItem.shortRatio + item.shortRatio;
        		String ratio = String.format("%.2f", stockItem.shortRatio);
        		stockItem.shortRatio = Double.parseDouble(ratio);
        	} else {
        		finalList.add(stockItem);
        		stockItem = item;
        	}
      
        }
        finalList.remove(0);
        return finalList;
    }
    
}