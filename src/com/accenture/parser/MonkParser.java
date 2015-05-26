package com.accenture.parser;

import com.accenture.utils.MasterTemplateUtil;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Stack;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MonkParser {
    private String filePath;
    private String tempFilePath;
    private String resultPath;
    private final HashMap localVariableValue = new HashMap();
    
    public void setFilePath(String filePath){
        this.filePath = filePath;
        this.setTempFilePath(filePath);
        this.setResultPath(filePath);
    }
    
    private String getFilePath(){
        return this.filePath;
    }
    
    private void setTempFilePath(String filePath){
        String[] tempPath= filePath.split("\\\\");
        String fileName = tempPath[tempPath.length -1], tempResultFilePath = "";
        String[] appendFileName = fileName.split("\\.");
        fileName = appendFileName[0]+"_tmp.txt";
        for(int i =0 ; i< tempPath.length-1; i++){
            tempResultFilePath += (tempPath[i]+"\\");
        }
        tempResultFilePath += fileName;
        this.tempFilePath = tempResultFilePath;
    }
    
    private String getTempFilePath(){
        return this.tempFilePath;
    }
    
    private void setResultPath(String filePath){
        String[] tempPath= filePath.split("\\\\");
        String fileName = tempPath[tempPath.length -1], tempResultFilePath = "";
        String[] appendFileName = fileName.split("\\.");
        fileName = appendFileName[0]+".csv";
        for(int i =0 ; i< tempPath.length-1; i++){
            tempResultFilePath += (tempPath[i]+"\\");
        }
        tempResultFilePath += fileName;
        this.resultPath = tempResultFilePath;
//        this.resultPath = "C:\\Users\\dheeraj\\Desktop\\Legacy_xl_EclipsysPRD_VXU_to_ARRA_TR.csv";
    }
    
    public String getResultPath(){
        return this.resultPath;
    }
    
    private void setLocalVariableValue(String key, String value){
        this.localVariableValue.put(key, value);
    }
    
    private HashMap getLocalVariableValue(){
        return this.localVariableValue;
    }
    
    public void readFile() throws IOException{
        BufferedReader br = new BufferedReader(new FileReader(new File(this.getFilePath())));
        String value;
        boolean logicStarted = false;
        String concatinatedValue = "";
        FileWriter fw = new FileWriter(new File(this.getTempFilePath()));
        try (BufferedWriter writeToFile = new BufferedWriter(fw)) {
            while((value = br.readLine())!= null){
                if(value.contains("(copy-strip") || value.contains("(if") 
                    || value.contains("(insert") || value.contains("(do")
                    || value.contains("(set")){
                    logicStarted = true;
                }
                if(logicStarted){
                    concatinatedValue += value;
                    if(!this.isRoutingRuleFile()){
                        writeToFile.write(value.trim());
                        writeToFile.write(System.lineSeparator());
                    }
                }
            }
        }
        if(this.isRoutingRuleFile()){
            this.writeContentToTempFile(this.fetchLogicForRR(concatinatedValue));
        }
    }
    
    public void constructFinalLogic(MasterTemplateUtil masterTemplate) throws FileNotFoundException, IOException {
        BufferedReader br = new BufferedReader(new FileReader(new File(this.getTempFilePath())));
        String value = "", condition = "";
        FileWriter fw = new FileWriter(new File(this.getResultPath()));
        int initialBraceCount = 0;
        try (BufferedWriter writeToFile = new BufferedWriter(fw)) {
            Stack<String> previousValue =new Stack<>();
            boolean isCurrentIf = false;
            while((value = br.readLine())!= null){
                int occuranceOfOpenBrace = this.getCharacterOccurrencesOf(value, '(');
                int occuranceOfCloseBrace = this.getCharacterOccurrencesOf(value, ')');
                int currentBraceCount = initialBraceCount+(occuranceOfOpenBrace - occuranceOfCloseBrace);
                if(initialBraceCount > currentBraceCount){
                    if(previousValue.size() > 0){
                        previousValue.pop();
                    }
                }
                condition = this.constructCondition(previousValue);
                if(this.isRoutingRuleFile() && value.contains("string=?") || value.contains("string-begins-with?")){
                    writeToFile.write(this.constructExpresion(value, "", masterTemplate));
                    writeToFile.write(System.lineSeparator());
                }else if(value.contains("copy-strip") || value.contains("insert")){
                    writeToFile.write(this.constructExpresion(value, condition, masterTemplate));
                    writeToFile.write(System.lineSeparator());
                }else if(value.contains("(set!") || value.contains("(let")){
                    this.assignValueForVariables(condition, value.split(" "), masterTemplate);
                }else if(value.trim().contains("(do")){
                    String[] doLogic = value.split(" ");
                    String result = "";
                    result = this.consrtuctFieldName(result, doLogic[doLogic.length-1].split("\\."), masterTemplate);
                    previousValue.push("Iterate over "+result.replaceAll("\\)", ""));
                }else if(value.trim().contains("(if") && 
                        (value.trim().contains("(and")|| value.trim().contains("(or")) ){
                    isCurrentIf = true;
                    String[] multipleConditions = value.split("\\(");
                    System.out.println("********************************************************");
                    String firstCondition = this.constructExpresion(multipleConditions[3], "", masterTemplate);
                    System.out.println(firstCondition);
                    String secondCondition = this.constructExpresion(multipleConditions[4], "", masterTemplate);
                    System.out.println(firstCondition);
                    System.out.println("********************************************************");
                    String operator = multipleConditions[2].toUpperCase();
                    String constructValue = "IF {"+firstCondition+" "+operator+" "+secondCondition+"}";
                    previousValue.push(constructValue);
                }else if(value.trim().contains("(if")){
                    isCurrentIf = true;
                    previousValue.push("IF {"+this.constructExpresion(value.replace("(if", "").trim(), "", masterTemplate)+"}");
                }else if(isCurrentIf && value.trim().contains("(begin")){
                    previousValue.push(" ");
                    isCurrentIf = false;
                }else if(value.trim().contains("(begin")){
                    if(previousValue.size() > 0){
                        value = previousValue.lastElement().replace("IF {", "");
                    }else{
                        value = "";
                    }
                    if(!value.equals("") || !value.equals(" ")){
                        previousValue.push("IF NOT {"+value);
                    }else{
                        previousValue.push(" ");
                    }
                }else if(initialBraceCount < currentBraceCount){
                    previousValue.push(" ");
                }
                initialBraceCount = currentBraceCount;
            }
        }
        File tempFile = new File(this.getTempFilePath());
        tempFile.delete();
    }

    private String constructExpresion(String value,String additionalCondition,MasterTemplateUtil masterTemplate) {
        value = this.processExpressionLogic(value);
        String[] expression = value.split(" ");
        int expressionSize = expression.length;
        String condition = expression[0], result ="", fieldName ="";
        additionalCondition += " ";
        if(value.contains("Is_Equal_to")){
            result = expression[1];
            fieldName = this.consrtuctFieldName(fieldName, expression[2].split("\\."), masterTemplate);
        }else if(value.contains("contains")){
            result = expression[1];
            fieldName = this.consrtuctFieldName(fieldName, expression[2].split("\\."), masterTemplate);
        }else if(value.contains("Concatinate")){
            for(int i = 2; i<expressionSize-2; i++){
                if(i == expressionSize-3){
                    result += this.consrtuctFieldName(fieldName, expression[i].split("\\."), masterTemplate);
                }else{
                    result += (this.consrtuctFieldName(fieldName, expression[i].split("\\."), masterTemplate)+"+");
                }
            }
            result = ",,,"+additionalCondition+" Concatinate following value("+result.replace("\"+\"", "\" \"")+")";
            fieldName = this.consrtuctFieldName(fieldName, expression[expressionSize-2].split("\\."), masterTemplate);
        }else if(value.contains("String_length")){
            condition = expression[0].replace("(", "");
            result = expression[3];
            fieldName = "String length of "+this.consrtuctFieldName(fieldName, expression[2].split("\\."), masterTemplate)+" is";
        }else if(value.contains("Begins_with")){
            result = expression[2];
            fieldName = this.consrtuctFieldName(fieldName, expression[1].split("\\."), masterTemplate);
        }else if(value.contains("Is_Empty")){
            fieldName = this.consrtuctFieldName(fieldName, expression[1].split("\\."), masterTemplate);
        }else if(value.contains("Transform")){
            for (int i =1; i<expressionSize-2 ; i++){
                result = result+" "+expression[i];
            }
            result = ",,,"+additionalCondition+" set as"+result;
            fieldName = this.consrtuctFieldName(fieldName, expression[expressionSize-2].split("\\."), masterTemplate)+",,,,";
        }else if(value.contains("Copy") && expression.length > 2){
            String tempFieldName = this.consrtuctFieldName(fieldName, expression[1].split("\\."), masterTemplate);
            tempFieldName = this.assignFieldNameIfVariable(tempFieldName);
            String additionalFunction = expression[3].trim().replace(")", "");
            additionalFunction = additionalFunction.replaceAll("\"", "");
            if(!additionalFunction.isEmpty()){
                additionalFunction = additionalFunction.replaceAll("%0", "");
                additionalFunction = " Copy 1st character to "+additionalFunction.replaceAll("d", "")+"th character from ";
            }else{
                additionalFunction = " Copy from ";
            }
            result = ",,,"+additionalCondition+additionalFunction+tempFieldName;
            fieldName = this.consrtuctFieldName(fieldName, expression[2].split("\\."), masterTemplate)+",,,,";
        }
        result = result.replaceAll("\\)", "");
        value = fieldName+" "+condition+" "+result;
        return value;
    }

    private boolean isRoutingRuleFile() {
        String[] fileFormatArray = this.filePath.split("\\.");
        String fileFormat = fileFormatArray[fileFormatArray.length - 1];
        return "isc".equals(fileFormat);
    }

    private String fetchLogicForRR(String concatinatedValue) {
        int endPosition = 0;
        Stack<Integer> stk = new Stack<Integer>();
        for (int i = 0; i < concatinatedValue.length(); i++) {
            char ch = concatinatedValue.charAt(i);
            if (ch == '(') {
                stk.push(i);
            } else if (ch == ')') {
                try {
                    if(stk.pop()+1 == 1){
                        endPosition = i + 1;
                    }
                }catch(Exception e){
                }
            }
        }
        return concatinatedValue.substring(0, endPosition);
    }

    private void writeContentToTempFile(String finalValue) throws IOException {
        FileWriter fw = new FileWriter(new File(this.getTempFilePath()));
        try (BufferedWriter writeToFile = new BufferedWriter(fw)) {
            for(int k =0; k< finalValue.length(); k++){
                char currentChar = finalValue.charAt(k);
                if(currentChar == '(' ){
                    writeToFile.write(System.lineSeparator()+"(");
                }else {
                    writeToFile.write(currentChar);
                }
            }
        }
    }

    private String consrtuctFieldName(String fieldName, String[] hl7Field, MasterTemplateUtil masterTemplate) {
        for(int i=0; i<hl7Field.length ;i++){
            if(i > 1 && i != hl7Field.length-1){
                fieldName += (hl7Field[i]+".");
            }else if(i == hl7Field.length-1){
                fieldName += hl7Field[i];
            }
        }
        fieldName = fieldName.replaceAll("\\)", "");
        if(!masterTemplate.getStandardTemplateName(fieldName).equals("")){
            fieldName = masterTemplate.getStandardTemplateName(fieldName);
        }
        return fieldName;
    }

    private int getCharacterOccurrencesOf(String value, char pattern) {
        int i=0;
        for(int j=0; j < value.length(); j++){
            if(value.charAt(j) == pattern){
                i++;
            }
        }
        return i;
    }

    private String assignFieldNameIfVariable(String tempFieldName) {
        if(tempFieldName.contains(":")){
            String[] tempFieldNameForSubString = tempFieldName.split(":");
            tempFieldName = "subString "+tempFieldNameForSubString[1].replace(",", " to ")+") from "+tempFieldNameForSubString[0];
        }
//TO DO
        HashMap replaceLocalVariableValue = this.getLocalVariableValue();
        if(replaceLocalVariableValue.size() > 0){
            if(replaceLocalVariableValue.get(tempFieldName) != null){
//                tempFieldName = (String) replaceLocalVariableValue.get(tempFieldName);
                tempFieldName = "##########"+tempFieldName;
            }
        }
        return tempFieldName;
    }

    private String constructCondition(Stack<String> previousValue) {
        String condition = "",previousCondition ="", currentCondition ="";
        for (String value : previousValue) {
            if(!value.equals(" ") && !value.contains("segment_ID")){
                if(!condition.equals(" ") && !condition.equals("")){
                    if(!value.contains("IF NOT")){
                        if(previousCondition.length() > 0){
                            previousCondition = previousCondition+" AND "+currentCondition;
                        }else{
                            previousCondition = currentCondition;
                        }
                    }
                    currentCondition = value;
                    if(previousCondition.isEmpty()){
                        condition = currentCondition;
                    }else{
                        condition = previousCondition+" AND "+currentCondition;
                    }
                }else{
                    currentCondition = value;
                    condition = currentCondition;
                }
            }
        }
        return condition;
    }

    private void assignValueForVariables(String condition, String[] variables, MasterTemplateUtil masterTemplate) {
        String fieldName = "", previousValues = "",previousCondition = "";
        if(this.getLocalVariableValue().get(variables[1]) != null){
            previousValues = this.getLocalVariableValue().get(variables[1]).toString();
            previousCondition = this.getLocalVariableValue().get(variables[1]).toString().split("set as")[0];
        }
        fieldName = condition+" set as "+this.consrtuctFieldName(fieldName, variables[2].split("\\."), masterTemplate);
        this.setLocalVariableValue(variables[1],fieldName);
    }

    private String processExpressionLogic(String value) {
        value = value.replaceFirst("\\(", "");
        HashMap logicList = new HashMap();
        logicList.put("string=?", "Is_Equal_to");
        logicList.put("regex", "contains");
        logicList.put("string-begins-with?", "Begins_with");
        logicList.put("string-length", "String_length");
        logicList.put("empty-string?", "Is_Empty");
        logicList.put("copy-strip", "Copy");
        logicList.put("insert", "Transform");
        logicList.put("string-append", "Concatinate");
//        TO DO:
//        load, data-map, duplicate-strip, let, find-get-before, custom-find-get-after, unique_id,
//        custom-change-pattern, list-lookup.
        Iterator entries = logicList.entrySet().iterator();
        while (entries.hasNext()) {
          Entry thisEntry = (Entry) entries.next();
          String key = thisEntry.getKey().toString();
          String replacementValue = thisEntry.getValue().toString();
          if(value.contains(key)){
              value = value.replace(key, replacementValue);
          }
        }
        return value;
    }

//    public static void main(String[] args){
//        MonkParser monkParser = new MonkParser();
//        MasterTemplateUtil masterTemplate = new MasterTemplateUtil();
//        masterTemplate.buildMasterForAllSegement();
//        monkParser.setFilePath("C:\\Users\\dheeraj\\Desktop\\xl_EclipsysPRD_VXU_to_ARRA.tsc");
//        try {
//            monkParser.readFile();
//            monkParser.constructFinalLogic(masterTemplate);
//        } catch (FileNotFoundException ex) {
//            System.out.println("Was Not able to find file:   "+ex);
//        } catch (IOException ex) {
//            System.out.println("Reading File has some difficulties:   "+ex);
//        }
//    }
    
}
