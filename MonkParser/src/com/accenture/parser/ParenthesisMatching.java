package com.accenture.parser;

import java.util.Scanner;
import java.util.Stack;

public class ParenthesisMatching {

    public static void main(String[] args) {
        Stack<Integer> stk = new Stack<Integer>();
        System.out.println("Enter expression");
        String exp = "(if (and (MSH-5-receiving_application) (PV1-3-assigned_patient_location.PL.fac_ID))(begin(#t))(begin(#f))))))(message-clear input)result))))";
        int sucessPos = 0;
        int len = exp.length();
        System.out.println("\nMatches and Mismatches:\n");
        for (int i = 0; i < len; i++) {
            char ch = exp.charAt(i);
            if (ch == '(') {
                stk.push(i);
            } else if (ch == ')') {
                try {
                    int p = stk.pop() + 1;
                    System.out.println("')' at index " + (i + 1) + " matched with ')' at index " + p);
                    if(p == 1){
                        sucessPos = i + 1;
                    }
                } catch (Exception e) {}
            }
        }
        System.out.println(">>>>>" + exp.substring(0, sucessPos));
    }
}
